package com.aquib.aiagent.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

class ScreenshotProvider {
    fun capturePlaceholder(): ByteArray {
        var bitmap: Bitmap? = null
        var stream: ByteArrayOutputStream? = null
        return try {
            bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xFF222222.toInt())
            stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        } finally {
            bitmap?.recycle()
            stream?.close()
        }
    }
}
