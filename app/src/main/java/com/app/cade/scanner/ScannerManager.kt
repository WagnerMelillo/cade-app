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
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import com.app.cade.data.DiscoveredContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.UUID
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

// UUID fixo do Aplicativo para reconhecer dispositivos do app Cade.
val CADE_SERVICE_UUID: UUID = UUID.fromString("0000CADE-0000-1000-8000-00805F9B34FB")
val parcelUuid = ParcelUuid(CADE_SERVICE_UUID)

class ScannerManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // Current State
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredContact>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredContact>> = _discoveredDevices

    // Distance & Target (para Bússola) - Atualizado pela estimativa GPS ou RSSI
    private val _currentTargetDistance = MutableStateFlow(0.0)
    val currentTargetDistance: StateFlow<Double> = _currentTargetDistance

    // Usaremos a última localização (Fused / Dead Reckoning backend)
    var lastKnownLocation: Location? = null

    init {
        updateMyFusedLocation()
    }

    @SuppressLint("MissingPermission")
    fun updateMyFusedLocation() {
        if (hasLocationPermission()) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        lastKnownLocation = location
                    }
                }
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(userId: String) {
        if (bluetoothAdapter == null || !hasBluetoothAdvertisePermission()) return

        val advertiser = bluetoothAdapter.bluetoothLeAdvertiser ?: return
        
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        // Como payload, enviamos os bytes de ID. Para encurtar, pegamos os primeiros 4 bytes do ID.
        // Em um app real complexo, codificariamos as coordenadas do FusedLocation.
        val userIdBytes = userId.toByteArray().take(4).toByteArray()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(parcelUuid)
            .addServiceData(parcelUuid, userIdBytes)
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (bluetoothAdapter == null || !hasBluetoothAdvertisePermission()) return
        bluetoothAdapter.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (bluetoothAdapter == null || !hasBluetoothScanPermission()) return
        val scanner = bluetoothAdapter.bluetoothLeScanner ?: return
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // Scan Filter opcional, mas vamos pegar tudo e filtrar no callback para simplificar o lab
        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (bluetoothAdapter == null || !hasBluetoothScanPermission()) return
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            // Log.d("ScannerManager", "Advertising started")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            result.scanRecord?.serviceUuids?.let { uuids ->
                if (uuids.contains(parcelUuid)) {
                    val serviceData = result.scanRecord?.getServiceData(parcelUuid)
                    val discoveredId = serviceData?.let { String(it) } ?: result.device.address
                    
                    val rssi = result.rssi
                    // Calculo de Distância baseado no RSSI do Bluetooth
                    val distance = calculateDistance(rssi, -59) 

                    val newDevice = DiscoveredContact(
                        id = discoveredId,
                        name = result.device.name ?: "Desconhecido", // name requires connect permission
                        lastRssi = rssi,
                        distance = distance
                    )

                    updateDeviceList(newDevice)
                    _currentTargetDistance.value = distance
                }
            }
        }
    }

    private fun updateDeviceList(device: DiscoveredContact) {
        val currentList = _discoveredDevices.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == device.id }
        if (index != -1) {
            currentList[index] = device
        } else {
            currentList.add(device)
        }
        _discoveredDevices.value = currentList
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

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothAdvertisePermission(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }
}
