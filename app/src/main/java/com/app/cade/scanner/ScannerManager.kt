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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
        beginAdvertise()
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        isAdvertising = false
        if (bluetoothAdapter == null || !hasBluetoothAdvertisePermission()) return
        bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun restartAdvertising() {
        if (bluetoothAdapter == null || !hasBluetoothAdvertisePermission()) return
        bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        beginAdvertise()
    }

    @SuppressLint("MissingPermission")
    private fun beginAdvertise() {
        if (bluetoothAdapter == null || !hasBluetoothAdvertisePermission()) return
        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return

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

    /** payload = [MAGIC(2)] + [radarId ASCII(8)] + [latE6 Int(4)] + [lonE6 Int(4)] = 18 bytes */
    private fun buildPayload(): ByteArray {
        val idBytes = myRadarId.toByteArray(Charsets.US_ASCII) // sempre 8 bytes
        val latInt = lastKnownLocation?.let { (it.latitude * 1_000_000).toInt() } ?: UNKNOWN_COORD
        val lonInt = lastKnownLocation?.let { (it.longitude * 1_000_000).toInt() } ?: UNKNOWN_COORD
        return ByteBuffer.allocate(2 + 8 + 4 + 4)
            .put(MAGIC)
            .put(idBytes)
            .putInt(latInt)
            .putInt(lonInt)
            .array()
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            // errorCode útil para diagnóstico (ex.: ADVERTISE_FAILED_DATA_TOO_LARGE)
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
            if (mfg.size < 18) return
            if (mfg[0] != MAGIC[0] || mfg[1] != MAGIC[1]) return

            val buf = ByteBuffer.wrap(mfg)
            buf.position(2)
            val idBytes = ByteArray(8)
            buf.get(idBytes)
            val id = String(idBytes, Charsets.US_ASCII).trim()
            val latInt = buf.int
            val lonInt = buf.int
            val lat = if (latInt == UNKNOWN_COORD) null else latInt / 1_000_000.0
            val lon = if (lonInt == UNKNOWN_COORD) null else lonInt / 1_000_000.0

            val rssi = result.rssi
            val distance = calculateDistance(rssi, -59)

            updateDeviceList(
                DiscoveredContact(
                    id = id,
                    name = id,
                    lastRssi = rssi,
                    distance = distance,
                    lat = lat,
                    lon = lon,
                    lastSeen = System.currentTimeMillis()
                )
            )
        }
    }

    private fun updateDeviceList(device: DiscoveredContact) {
        val now = System.currentTimeMillis()
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.id == device.id }
        if (index != -1) current[index] = device else current.add(device)
        _discoveredDevices.value = current.filter { now - it.lastSeen < STALE_MS }
    }

    private fun calculateDistance(rssi: Int, txPower: Int): Double {
        if (rssi == 0) return -1.0
        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            Math.pow(ratio, 10.0)
        } else {
            (0.89976) * Math.pow(ratio, 7.7095) + 0.111
        }
    }

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
