package com.app.cade.scanner

import android.content.Context
import android.util.Log

/**
 * Detecção REAL de suporte a UWB (Ultra Wideband), conforme exigido no UWB.docx.
 *
 * Nada cosmético: usa o PackageManager para perguntar ao próprio Android se o
 * hardware/SO declara o recurso "android.hardware.uwb". Aparelhos sem o chip
 * (a maioria) retornam false e a opção de UWB fica desabilitada na interface.
 */
object UwbCapabilityHelper {

    private const val TAG = "UwbCapability"
    private const val FEATURE_UWB = "android.hardware.uwb"

    fun isUwbSupported(context: Context): Boolean {
        return try {
            val supported = context.packageManager.hasSystemFeature(FEATURE_UWB)
            Log.d(TAG, "Suporte real a UWB neste aparelho: $supported")
            supported
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao verificar suporte a UWB; assumindo indisponível.", e)
            false
        }
    }
}
