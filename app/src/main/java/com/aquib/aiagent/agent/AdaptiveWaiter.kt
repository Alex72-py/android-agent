package com.aquib.aiagent.agent

import android.os.SystemClock
import kotlinx.coroutines.delay

class AdaptiveWaiter {
    suspend fun waitUntil(timeoutMs: Long = 10_000L, pollMs: Long = 250L, condition: () -> Boolean): Boolean {
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            if (condition()) return true
            delay(pollMs)
        }
        return false
    }

    suspend fun waitForSettle(snapshot: () -> Int, timeoutMs: Long = 8_000L): Boolean {
        var stableCount = 0
        var last = snapshot()
        return waitUntil(timeoutMs = timeoutMs, pollMs = 300L) {
            val current = snapshot()
            if (current == last) {
                stableCount++
            } else {
                stableCount = 0
                last = current
            }
            stableCount >= 3
        }
    }
}
