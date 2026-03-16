package com.aquib.aiagent

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.aquib.aiagent.ai.GeminiApiClient
import com.aquib.aiagent.overlay.AgentOverlayService
import com.aquib.aiagent.telemetry.Telemetry
import com.aquib.aiagent.util.SecurePrefs
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var securePrefs: SecurePrefs
    private lateinit var api: GeminiApiClient
    private lateinit var telemetry: Telemetry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        securePrefs = SecurePrefs(this)
        api = GeminiApiClient(this)
        telemetry = Telemetry(this)

        val tvChecklist = findViewById<TextView>(R.id.tvChecklist)
        val tvStats = findViewById<TextView>(R.id.tvStats)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSave = findViewById<Button>(R.id.btnSaveKey)
        val btnFixBattery = findViewById<Button>(R.id.btnFixBattery)

        etApiKey.setText(securePrefs.getString("active_api_key"))
        btnSave.setOnClickListener {
            securePrefs.putString("active_api_key", etApiKey.text?.toString().orEmpty().trim())
            refreshStats(tvStats)
            Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
        }

        btnFixBattery.setOnClickListener { requestBatteryExemption() }

        requestRuntimePermissions()
        maybeStartOverlayService()
        tvChecklist.text = buildChecklist()
        refreshStats(tvStats)
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.tvChecklist).text = buildChecklist()
        refreshStats(findViewById(R.id.tvStats))
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed += android.Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed += android.Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), 7)
    }

    private fun refreshStats(tvStats: TextView) {
        lifecycleScope.launch {
            val lite = api.dailyStats(complex = false)
            val remaining = lite.limit - lite.used
            val pct = if (lite.limit == 0) 0f else (remaining.toFloat() / lite.limit)
            val color = when {
                pct < 0.1f -> R.color.danger
                pct < 0.2f -> R.color.warn
                else -> R.color.text_primary
            }
            tvStats.setTextColor(ContextCompat.getColor(this@MainActivity, color))
            tvStats.text = buildString {
                appendLine("API calls today: ${lite.used} / ${lite.limit} remaining: $remaining")
                appendLine(telemetry.summary())
            }
        }
    }

    private fun buildChecklist(): String {
        val powerManager = getSystemService(PowerManager::class.java)
        val batteryOk = powerManager.isIgnoringBatteryOptimizations(packageName)
        val overlayOk = Settings.canDrawOverlays(this)
        val accessibilityOk = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        val micOk = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        val notifOk = if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        return """
Overlay: ${status(overlayOk)}
Accessibility: ${status(accessibilityOk)}
Microphone: ${status(micOk)}
Notifications: ${status(notifOk)}
Battery Optimization Exemption: ${status(batteryOk)}
""".trimIndent()
    }

    private fun status(value: Boolean): String = if (value) "✅ Enabled" else "❌ Missing"

    private fun requestBatteryExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun maybeStartOverlayService() {
        if (!Settings.canDrawOverlays(this)) return
        val overlayIntent = Intent(this, AgentOverlayService::class.java)
        ContextCompat.startForegroundService(this, overlayIntent)
    }
}
