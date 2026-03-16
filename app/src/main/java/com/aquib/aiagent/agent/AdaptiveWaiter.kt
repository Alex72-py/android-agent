package com.aquib.aiagent.agent

import android.os.SystemClock
import kotlinx.coroutines.delay

class AdaptiveWaiter {
    suspend fun waitUntil(timeoutMs: Long = 10_000L, condition: () -> Boolean): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (condition()) return true
            delay(250)
        }
        return false
    }
}
