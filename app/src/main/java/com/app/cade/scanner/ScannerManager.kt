package com.app.cade.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.app.cade.data.DiscoveredContact
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.core.uwb.UwbManager
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Camada de RÁDIO + LOCALIZAÇÃO do CADÊ.
 *
 * O que ela faz de verdade:
 *  - Anuncia (advertise) via BLE um pacote com: assinatura do app + radarId (8)
 *    + minhas coordenadas (lat/lon) vindas do FusedLocation (que usa GPS, WiFi e
 *    rede — é o mesmo posicionamento assistido do Google usado em ambientes
 *    internos). Reanuncia quando a localização muda.
 *  - Escaneia BLE, filtra só pacotes do CADÊ e extrai o radarId + coordenadas
 *    do outro aparelho, além do RSSI (intensidade do sinal).
 *
 * Com isso, o ViewModel consegue calcular DISTÂNCIA e DIREÇÃO reais até o alvo.
 */
class ScannerManager(private val context: Context) {

    companion object {
        // 0xFFFF é reservado pelo Bluetooth SIG para testes/uso interno.
        const val MANUFACTURER_ID = 0xFFFF
        // Assinatura ("CA","DE") dentro do payload p/ não confundir com outros apps.
        val MAGIC = byteArrayOf(0xCA.toByte(), 0xDE.toByte())
        const val UNKNOWN_COORD = Int.MIN_VALUE
        const val STALE_MS = 20_000L // some do radar se não for visto há 20s

        const val ROLE_CONTROLLER = 1
        const val ROLE_CONTROLEE = 2
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredContact>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredContact>> = _discoveredDevices

    private val _myLocation = MutableStateFlow<Location?>(null)
    val myLocation: StateFlow<Location?> = _myLocation

    var lastKnownLocation: Location? = null
        private set

    private var isAdvertising = false
    private var myRadarId: String = "00000000"

    // --- UWB e Anúncio Alternado BLE ---
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private var advertisingJob: Job? = null
    private var advertiseType = 1 // 1 = Telemetria + UWB, 2 = Status Visual

    private var localUwbAddress: ByteArray? = null
    private var localComplexChannel: UwbComplexChannel? = null

    private val uwbManager: UwbManager? by lazy {
        if (UwbCapabilityHelper.isUwbSupported(context)) {
            try {
                UwbManager.createInstance(context)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    data class UwbRangingData(
        val distance: Float,
        val azimuth: Float?,
        val elevation: Float?
    )

    private val _uwbRangingResult = MutableStateFlow<UwbRangingData?>(null)
    val uwbRangingResult: StateFlow<UwbRangingData?> = _uwbRangingResult.asStateFlow()

    private var uwbSessionJob: Job? = null
    var activeUwbPeerId: String? = null
        private set
    var activeUwbRole: Int? = null // 1 = Controller, 2 = Controlee
        private set

    var currentTargetId: String = "00000000"
    var localVisualStatus: String = ""

    // ---------------- Localização (FusedLocation / Google) ----------------

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                lastKnownLocation = loc
                _myLocation.value = loc
                if (isAdvertising) restartAdvertising()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    lastKnownLocation = loc
                    _myLocation.value = loc
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // ---------------- Advertising (ficar visível) ----------------

    @SuppressLint("MissingPermission")
    fun startAdvertising(radarId: String) {
        myRadarId = radarId.padEnd(8, '0').take(8)
        isAdvertising = true
        advertiseType = 1

        ioScope.launch {
            if (localUwbAddress == null && uwbManager != null) {
                try {
                    val scope = uwbManager!!.controllerSessionScope()
                    localUwbAddress = scope.localAddress.address
                    localComplexChannel = scope.uwbComplexChannel
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            startAdvertisingLoop()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        isAdvertising = false
        advertisingJob?.cancel()
        advertisingJob = null
        if (bluetoothAdapter == null || !hasBluetoothAdvertisePermission()) return
        try {
            bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            // ignore
        }
    }

    @SuppressLint("MissingPermission")
    private fun restartAdvertising() {
        if (bluetoothAdapter == null || !hasBluetoothAdvertisePermission()) return
        beginAdvertise()
    }

    private fun startAdvertisingLoop() {
        advertisingJob?.cancel()
        advertisingJob = ioScope.launch {
            while (isAdvertising) {
                beginAdvertise()
                delay(2500)
                advertiseType = if (advertiseType == 1) 2 else 1
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun beginAdvertise() {
        if (bluetoothAdapter == null || !hasBluetoothAdvertisePermission()) return
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return

        try {
            advertiser.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            // ignore
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addManufacturerData(MANUFACTURER_ID, buildPayload())
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    /**
     * Alterna o payload para caber no limite rígido de 31 bytes do BLE legado.
     * Tipo 0x01: Telemetria + Parâmetros UWB (31 bytes)
     * Tipo 0x02: Status Visual (máximo 31 bytes)
     */
    private fun buildPayload(): ByteArray {
        val idBytes = myRadarId.toByteArray(Charsets.US_ASCII) // sempre 8 bytes
        if (advertiseType == 1) {
            val targetIdBytes = currentTargetId.padEnd(8, '0').take(8).toByteArray(Charsets.US_ASCII)
            val latInt = lastKnownLocation?.let { (it.latitude * 1_000_000).toInt() } ?: UNKNOWN_COORD
            val lonInt = lastKnownLocation?.let { (it.longitude * 1_000_000).toInt() } ?: UNKNOWN_COORD
            val uwbAddrBytes = localUwbAddress ?: byteArrayOf(0, 0)
            val channelVal = localComplexChannel?.channel ?: 9
            val preambleVal = localComplexChannel?.preambleIndex ?: 11

            return ByteBuffer.allocate(31)
                .put(MAGIC)
                .put(0x01.toByte())
                .put(idBytes)
                .put(targetIdBytes)
                .putInt(latInt)
                .putInt(lonInt)
                .put(uwbAddrBytes)
                .put(channelVal.toByte())
                .put(preambleVal.toByte())
                .array()
        } else {
            val statusBytes = localVisualStatus.take(20).toByteArray(Charsets.UTF_8)
            val len = 2 + 1 + 8 + statusBytes.size
            val buffer = ByteBuffer.allocate(len.coerceAtMost(31))
                .put(MAGIC)
                .put(0x02.toByte())
                .put(idBytes)
            if (statusBytes.isNotEmpty()) {
                buffer.put(statusBytes, 0, statusBytes.size.coerceAtMost(20))
            }
            return buffer.array()
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
        }
    }

    // ---------------- Scanning (procurar) ----------------

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (bluetoothAdapter == null || !hasBluetoothScanPermission()) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (bluetoothAdapter == null || !hasBluetoothScanPermission()) return
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val record = result.scanRecord ?: return
            val mfg = record.getManufacturerSpecificData(MANUFACTURER_ID) ?: return
            if (mfg.size < 11) return
            if (mfg[0] != MAGIC[0] || mfg[1] != MAGIC[1]) return

            val type = mfg[2].toInt()
            val buf = ByteBuffer.wrap(mfg)
            buf.position(3)
            val idBytes = ByteArray(8)
            buf.get(idBytes)
            val id = String(idBytes, Charsets.US_ASCII).trim()

            val rssi = result.rssi
            val distance = calculateDistance(rssi, -59)

            if (type == 0x01 && mfg.size >= 31) {
                val targetBytes = ByteArray(8)
                buf.get(targetBytes)
                val targetId = String(targetBytes, Charsets.US_ASCII).trim()

                val latInt = buf.int
                val lonInt = buf.int
                val lat = if (latInt == UNKNOWN_COORD) null else latInt / 1_000_000.0
                val lon = if (lonInt == UNKNOWN_COORD) null else lonInt / 1_000_000.0

                val uwbAddrBytes = ByteArray(2)
                buf.get(uwbAddrBytes)
                val uwbChannel = buf.get().toInt()
                val uwbPreamble = buf.get().toInt()

                updateDeviceList(
                    id = id,
                    rssi = rssi,
                    distance = distance,
                    lat = lat,
                    lon = lon,
                    targetId = targetId,
                    peerUwbAddress = uwbAddrBytes,
                    peerUwbChannel = uwbChannel,
                    peerUwbPreamble = uwbPreamble
                )
            } else if (type == 0x02) {
                val statusLen = mfg.size - 11
                val status = if (statusLen > 0) {
                    val statusBytes = ByteArray(statusLen)
                    buf.get(statusBytes)
                    String(statusBytes, Charsets.UTF_8).trim()
                } else {
                    ""
                }
                updateDeviceStatus(id, status)
            }
        }
    }

    private fun updateDeviceList(
        id: String,
        rssi: Int,
        distance: Double,
        lat: Double?,
        lon: Double?,
        targetId: String,
        peerUwbAddress: ByteArray,
        peerUwbChannel: Int,
        peerUwbPreamble: Int
    ) {
        val now = System.currentTimeMillis()
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }

        val oldContact = current.getOrNull(index)

        val updated = DiscoveredContact(
            id = id,
            name = oldContact?.name ?: id,
            lastRssi = rssi,
            distance = distance,
            lat = lat,
            lon = lon,
            lastSeen = now,
            visualStatus = oldContact?.visualStatus,
            securityCode = oldContact?.securityCode,
            uwbAddress = peerUwbAddress,
            uwbChannel = peerUwbChannel,
            uwbPreamble = peerUwbPreamble,
            targetId = targetId
        )

        if (index != -1) {
            current[index] = updated
        } else {
            current.add(updated)
        }

        _discoveredDevices.value = current.filter { now - it.lastSeen < STALE_MS }
    }

    private fun updateDeviceStatus(id: String, status: String) {
        val now = System.currentTimeMillis()
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val contact = current[index]
            current[index] = contact.copy(visualStatus = status, lastSeen = now)
            _discoveredDevices.value = current.filter { now - it.lastSeen < STALE_MS }
        }
    }

    // ---------------- UWB Ranging Session Lifecycle ----------------

    @SuppressLint("MissingPermission")
    fun startUwbRanging(
        peerId: String,
        role: Int,
        peerAddressBytes: ByteArray,
        channel: Int,
        preamble: Int
    ) {
        if (uwbManager == null) return
        if (activeUwbPeerId == peerId && activeUwbRole == role) return

        stopUwbRanging()

        activeUwbPeerId = peerId
        activeUwbRole = role

        uwbSessionJob = ioScope.launch {
            try {
                val sessionId = (myRadarId.hashCode() xor peerId.hashCode()).let { Math.abs(it) }
                val peerDevice = UwbDevice(UwbAddress(peerAddressBytes))

                if (role == ROLE_CONTROLLER) {
                    val sessionScope = uwbManager!!.controllerSessionScope()
                    val complexChannel = UwbComplexChannel(channel, preamble)
                    val parameters = RangingParameters(
                        uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                        sessionId = sessionId,
                        subSessionId = 0,
                        sessionKeyInfo = null,
                        subSessionKeyInfo = null,
                        complexChannel = complexChannel,
                        peerDevices = listOf(peerDevice),
                        updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
                    )
                    sessionScope.prepareSession(parameters).collect { result ->
                        handleRangingResult(result)
                    }
                } else {
                    val sessionScope = uwbManager!!.controleeSessionScope()
                    val parameters = RangingParameters(
                        uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
                        sessionId = sessionId,
                        subSessionId = 0,
                        sessionKeyInfo = null,
                        subSessionKeyInfo = null,
                        complexChannel = null,
                        peerDevices = listOf(peerDevice),
                        updateRateType = RangingParameters.RANGING_UPDATE_RATE_AUTOMATIC
                    )
                    sessionScope.prepareSession(parameters).collect { result ->
                        handleRangingResult(result)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activeUwbPeerId = null
                activeUwbRole = null
            }
        }
    }

    fun stopUwbRanging() {
        uwbSessionJob?.cancel()
        uwbSessionJob = null
        activeUwbPeerId = null
        activeUwbRole = null
        _uwbRangingResult.value = null
    }

    private fun handleRangingResult(result: RangingResult) {
        when (result) {
            is RangingResult.RangingResultPosition -> {
                val pos = result.position
                val dist = pos.distance?.value
                if (dist != null) {
                    val az = pos.azimuth?.value
                    val el = pos.elevation?.value
                    _uwbRangingResult.value = UwbRangingData(dist, az, el)
                }
            }
            is RangingResult.RangingResultPeerDisconnected -> {
                stopUwbRanging()
            }
        }
    }

    /**
     * Estimativa de distância (metros) a partir do RSSI. É APROXIMADA por
     * natureza (varia com obstáculos, corpo, orientação). Serve para o modo
     * "quente/frio", não para precisão métrica.
     */
    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            Math.pow(ratio, 10.0)
        } else {
            (0.89976) * Math.pow(ratio, 7.7095) + 0.111
        }
    }

    // ---------------- Permissões ----------------

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothAdvertisePermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasBluetoothScanPermission(): Boolean =
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED
}
