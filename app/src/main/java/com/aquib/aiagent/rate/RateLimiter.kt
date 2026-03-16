package com.aquib.aiagent.rate

import android.content.Context
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.util.ArrayDeque

class RateLimiter(context: Context) {
    enum class Model(val rpm: Int, val rpd: Int) {
        FLASH_LITE(15, 1000),
        FLASH(10, 250),
        PRO(5, 100)
    }

    data class DailyStats(val used: Int, val limit: Int)

    private val prefs = context.getSharedPreferences("rate_limiter", Context.MODE_PRIVATE)
    private val callsWindowMs = 60_000L
    private val calls = mutableMapOf<Model, ArrayDeque<Long>>()

    suspend fun awaitAndAcquire(model: Model): Boolean {
        resetIfNewDay()
        val used = prefs.getInt(dayKey(model), 0)
        if (used >= model.rpd) return false

        val queue = calls.getOrPut(model) { ArrayDeque() }
        val now = System.currentTimeMillis()
        while (queue.isNotEmpty() && now - queue.first() > callsWindowMs) {
            queue.removeFirst()
        }

        if (queue.size >= model.rpm) {
            val waitMs = callsWindowMs - (now - queue.first()) + 50
            if (waitMs > 0) delay(waitMs)
        }

        val acquiredAt = System.currentTimeMillis()
        queue.addLast(acquiredAt)
        prefs.edit().putInt(dayKey(model), used + 1).apply()
        return true
    }

    fun stats(model: Model): DailyStats {
        resetIfNewDay()
        return DailyStats(prefs.getInt(dayKey(model), 0), model.rpd)
    }

    private fun resetIfNewDay() {
        val today = LocalDate.now(ZoneId.systemDefault()).toString()
        val stored = prefs.getString("date", "")
        if (stored != today) {
            prefs.edit().clear().putString("date", today).apply()
        }
    }

    private fun dayKey(model: Model): String = "${model.name}_day_count"
}
