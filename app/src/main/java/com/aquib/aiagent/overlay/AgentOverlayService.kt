package com.aquib.aiagent.overlay

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.aquib.aiagent.util.NotificationHelper

class AgentOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var panel: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1101, NotificationHelper.foregroundNotification(this))
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        panel?.let { windowManager.removeView(it) }
        panel = null
        super.onDestroy()
    }

    private fun createOverlay() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 140
        }

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xCC1C1C21.toInt())
            addView(TextView(context).apply {
                text = "AI Agent\nStage: Idle\nTap takeover in app"
                setTextColor(0xFFD0D0D8.toInt())
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
            })
        }
        windowManager.addView(panel, params)
    }
}
