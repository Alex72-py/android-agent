package com.aquib.aiagent.agent

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.abs

class ActionVerifier {
    fun changed(beforeBytes: ByteArray, afterBytes: ByteArray): Boolean {
        var before: Bitmap? = null
        var after: Bitmap? = null
        return try {
            before = BitmapFactory.decodeByteArray(beforeBytes, 0, beforeBytes.size)
            after = BitmapFactory.decodeByteArray(afterBytes, 0, afterBytes.size)
            if (before == null || after == null) return true
            if (before.width != after.width || before.height != after.height) return true

            val samplePoints = listOf(0.2f to 0.2f, 0.5f to 0.5f, 0.8f to 0.8f, 0.3f to 0.7f, 0.7f to 0.3f)
            var delta = 0
            samplePoints.forEach { (xf, yf) ->
                val x = (before.width * xf).toInt().coerceIn(0, before.width - 1)
                val y = (before.height * yf).toInt().coerceIn(0, before.height - 1)
                delta += abs(before.getPixel(x, y) - after.getPixel(x, y))
            }
            delta > 100
        } finally {
            before?.recycle()
            after?.recycle()
        }
    }
}
