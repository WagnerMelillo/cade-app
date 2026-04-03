package com.app.cade.utils

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QRCodeGenerator {
    fun generateQRCode(text: String, size: Int = 512): ImageBitmap? {
        if (text.isBlank()) return null
        return try {
            val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, 
                        y, 
                        if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                    )
                }
            }
            bitmap.asImageBitmap()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
