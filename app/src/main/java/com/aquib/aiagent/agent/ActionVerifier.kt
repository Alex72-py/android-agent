package com.aquib.aiagent.agent

import android.graphics.Bitmap
import android.graphics.BitmapFactory

class ActionVerifier {
    fun changed(beforeBytes: ByteArray, afterBytes: ByteArray): Boolean {
        var before: Bitmap? = null
        var after: Bitmap? = null
        return try {
            before = BitmapFactory.decodeByteArray(beforeBytes, 0, beforeBytes.size)
            after = BitmapFactory.decodeByteArray(afterBytes, 0, afterBytes.size)
            if (before == null || after == null) return true
            if (before.width != after.width || before.height != after.height) return true
            val x = before.width / 2
            val y = before.height / 2
            before.getPixel(x, y) != after.getPixel(x, y)
        } finally {
            before?.recycle()
            after?.recycle()
        }
    }
}
