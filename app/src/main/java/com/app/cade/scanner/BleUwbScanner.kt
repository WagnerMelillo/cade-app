package com.app.cade.scanner

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class BleUwbScanner(private val context: Context) {
    // Calculador híbrido simulado UWB/BLE 
    fun startScanning(targetAddress: String): Flow<Double> = flow {
        var currentDistance = 20.0
        while (true) {
            // Simulador de aproximação em metros
            if (currentDistance > 1.0) {
                currentDistance -= 0.5
            }
            emit(currentDistance)
            delay(1000)
        }
    }
}
