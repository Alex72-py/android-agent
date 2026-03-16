package com.aquib.aiagent.rate

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val lock = Mutex()

    suspend fun awaitAndAcquire(model: Model): Boolean = lock.withLock {
        resetIfNewDay()
        val used = prefs.getInt(dayKey(model), 0)
        if (used >= model.rpd) return@withLock false

        val queue = calls.getOrPut(model) { ArrayDeque() }
        while (queue.isNotEmpty() && System.currentTimeMillis() - queue.first() > callsWindowMs) {
            queue.removeFirst()
        }

        if (queue.size >= model.rpm) {
            val waitMs = callsWindowMs - (System.currentTimeMillis() - queue.first()) + 50
            if (waitMs > 0) delay(waitMs)
            while (queue.isNotEmpty() && System.currentTimeMillis() - queue.first() > callsWindowMs) {
                queue.removeFirst()
            }
        }

        queue.addLast(System.currentTimeMillis())
        prefs.edit().putInt(dayKey(model), used + 1).apply()
        true
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
            calls.clear()
        }
    }

    private fun dayKey(model: Model): String = "${model.name}_day_count"
}
