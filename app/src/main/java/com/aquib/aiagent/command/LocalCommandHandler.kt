package com.aquib.aiagent.command

import android.content.Context
import android.content.Intent
import android.provider.Settings

class LocalCommandHandler(private val context: Context) {
    fun tryHandle(goal: String): Boolean {
        val g = goal.lowercase().trim()
        return when {
            g == "go home" -> true
            g.startsWith("open ") -> {
                if (g.contains("settings")) {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                true
            }
            else -> false
        }
    }
}
