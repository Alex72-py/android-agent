package com.aquib.aiagent.telemetry

import android.content.Context

data class TaskLog(
    val goal: String,
    val steps: Int,
    val success: Boolean,
    val durationMs: Long,
    val failures: Int
)

class Telemetry(context: Context) {
    private val prefs = context.getSharedPreferences("telemetry", Context.MODE_PRIVATE)

    fun record(log: TaskLog) {
        val total = prefs.getInt("total", 0) + 1
        val success = prefs.getInt("success", 0) + if (log.success) 1 else 0
        val steps = prefs.getInt("steps", 0) + log.steps
        prefs.edit()
            .putInt("total", total)
            .putInt("success", success)
            .putInt("steps", steps)
            .putString("last_failure", if (log.success) "" else log.goal)
            .apply()
    }

    fun summary(): String {
        val total = prefs.getInt("total", 0)
        val success = prefs.getInt("success", 0)
        val steps = prefs.getInt("steps", 0)
        val rate = if (total == 0) 0 else (success * 100 / total)
        val avg = if (total == 0) 0 else (steps / total)
        return "Tasks: $total\nSuccess Rate: $rate%\nAvg Steps: $avg\nRecent Failure: ${prefs.getString("last_failure", "None")}".trim()
    }

    fun failurePatternHint(): String = prefs.getString("last_failure", "") ?: ""
}
