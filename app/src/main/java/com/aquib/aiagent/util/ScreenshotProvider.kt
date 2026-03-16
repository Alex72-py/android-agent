package com.aquib.aiagent.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.accessibility.AccessibilityNodeInfo
import java.io.ByteArrayOutputStream

class ScreenshotProvider {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f
        color = Color.WHITE
    }

    fun captureSemanticFrame(root: AccessibilityNodeInfo?): ByteArray {
        var bitmap: Bitmap? = null
        var stream: ByteArrayOutputStream? = null
        return try {
            val lines = mutableListOf<String>()
            collect(root, lines)
            val normalized = if (lines.isEmpty()) listOf("EMPTY_TREE") else lines.take(30)

            bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFF202026.toInt())
            normalized.forEachIndexed { index, line ->
                canvas.drawText(line.take(90), 20f, 40f + index * 32f, paint)
            }

            stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            stream.toByteArray()
        } finally {
            bitmap?.recycle()
            stream?.close()
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        node ?: return
        val text = node.text?.toString().orEmpty().trim()
        val desc = node.contentDescription?.toString().orEmpty().trim()
        val id = node.viewIdResourceName.orEmpty()
        if (text.isNotBlank() || desc.isNotBlank() || id.isNotBlank()) {
            out += "$text|$desc|$id|click=${node.isClickable}"
        }
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), out)
        }
    }
}
