package com.aquib.aiagent.ai

import android.content.Context
import android.util.Base64
import com.aquib.aiagent.models.ActionType
import com.aquib.aiagent.models.Step
import com.aquib.aiagent.models.UiElement
import com.aquib.aiagent.rate.RateLimiter
import com.aquib.aiagent.util.SecurePrefs
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.delay
import org.json.JSONArray

class GeminiApiClient(context: Context) {
    private val securePrefs = SecurePrefs(context)
    private val rateLimiter = RateLimiter(context)

    suspend fun getPlan(
        screenshot: ByteArray,
        uiElements: List<UiElement>,
        appState: String,
        memory: String,
        goal: String,
        complex: Boolean
    ): List<Step> {
        val modelTier = if (complex) RateLimiter.Model.FLASH else RateLimiter.Model.FLASH_LITE
        val allowed = rateLimiter.awaitAndAcquire(modelTier)
        if (!allowed) throw IllegalStateException("Daily limit reached. Resets at midnight.")

        val prompt = buildPrompt(goal, appState, memory, uiElements, screenshot)
        val modelName = if (complex) "gemini-2.5-flash" else "gemini-2.5-flash-lite"
        val key = securePrefs.getString("active_api_key")
        if (key.isBlank()) return fallbackPlan(goal)

        val model = GenerativeModel(modelName = modelName, apiKey = key)
        repeat(4) { attempt ->
            try {
                val response = model.generateContent(content { text(prompt) })
                val parsed = parseSteps(response.text.orEmpty())
                if (parsed.isNotEmpty()) return parsed
            } catch (error: Exception) {
                val is429 = error.message?.contains("429") == true
                if (is429 && attempt < 3) {
                    delay((1 shl (attempt + 1)) * 1000L)
                } else if (attempt >= 3) {
                    return fallbackPlan(goal)
                }
            }
        }
        return fallbackPlan(goal)
    }

    suspend fun getRecoveryAction(
        screenshot: ByteArray,
        uiElements: List<UiElement>,
        failedStep: Step
    ): Step {
        val plan = getPlan(
            screenshot = screenshot,
            uiElements = uiElements,
            appState = "Recovery",
            memory = "",
            goal = "Recover from: $failedStep",
            complex = false
        )
        return plan.firstOrNull() ?: Step(ActionType.BACK)
    }

    fun dailyStats(complex: Boolean): RateLimiter.DailyStats {
        val model = if (complex) RateLimiter.Model.FLASH else RateLimiter.Model.FLASH_LITE
        return rateLimiter.stats(model)
    }

    private fun buildPrompt(
        goal: String,
        appState: String,
        memory: String,
        elements: List<UiElement>,
        screenshot: ByteArray
    ): String {
        val trimmed = elements.take(12).joinToString("\n") {
            "text=${it.text}, cd=${it.contentDescription}, id=${it.resourceId}, bounds=[${it.left},${it.top},${it.right},${it.bottom}]"
        }
        val head = screenshot.copyOfRange(0, minOf(screenshot.size, 1200))
        val screenshotHint = Base64.encodeToString(head, Base64.NO_WRAP)
        return """
You are a planner only. Never execute actions.
Goal: $goal
AppState: $appState
Memory:
$memory
Top Elements (max 12):
$trimmed
ScreenshotDigest(base64-head): $screenshotHint
Return strict JSON array only.
Schema: [{"action":"TAP|TYPE|SWIPE|SCROLL|BACK|HOME|WAIT","targetText":"","value":"","direction":""}]
""".trimIndent()
    }

    private fun parseSteps(raw: String): List<Step> {
        val jsonStart = raw.indexOf('[')
        val jsonEnd = raw.lastIndexOf(']')
        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) return emptyList()

        val arr = JSONArray(raw.substring(jsonStart, jsonEnd + 1))
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val action = runCatching {
                    ActionType.valueOf(obj.optString("action", "WAIT").uppercase())
                }.getOrDefault(ActionType.WAIT)
                add(
                    Step(
                        action = action,
                        targetText = obj.optString("targetText", "").ifBlank { null },
                        value = obj.optString("value", "").ifBlank { null },
                        direction = obj.optString("direction", "").ifBlank { null }
                    )
                )
            }
        }
    }

    private fun fallbackPlan(goal: String): List<Step> = listOf(
        Step(ActionType.WAIT, value = "Re-evaluating goal: $goal"),
        Step(ActionType.BACK)
    )
}
