package com.aquib.aiagent.agent

import com.aquib.aiagent.accessibility.AccessibilityAgentService
import com.aquib.aiagent.ai.GeminiApiClient
import com.aquib.aiagent.memory.TaskMemory
import com.aquib.aiagent.models.ActionType
import com.aquib.aiagent.models.ExecutionResult
import com.aquib.aiagent.models.Step
import com.aquib.aiagent.models.UiElement
import com.aquib.aiagent.planner.TaskPlanner
import com.aquib.aiagent.safety.SafetyLayer
import com.aquib.aiagent.state.AppStateDetector
import com.aquib.aiagent.telemetry.TaskLog
import com.aquib.aiagent.telemetry.Telemetry
import com.aquib.aiagent.ui.ElementRanker
import com.aquib.aiagent.ui.UIExtractor
import com.aquib.aiagent.util.ScreenshotProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentLoop(
    private val planner: TaskPlanner,
    private val apiClient: GeminiApiClient,
    private val memory: TaskMemory,
    private val telemetry: Telemetry,
    private val safetyLayer: SafetyLayer,
    private val stateDetector: AppStateDetector,
    private val uiExtractor: UIExtractor,
    private val elementRanker: ElementRanker,
    private val actionVerifier: ActionVerifier,
    private val waiter: AdaptiveWaiter,
    private val screenshotProvider: ScreenshotProvider
) {
    suspend fun executeGoal(goal: String, onLog: (String) -> Unit, onConfirm: suspend (Step) -> Boolean): ExecutionResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        var totalSteps = 0
        var failures = 0

        for ((index, task) in planner.build(goal).withIndex()) {
            onLog("Stage ${index + 1}: ${task.goal}")
            val root = AccessibilityAgentService.instance?.root()
            val elements = uiExtractor.extract(root)
            val appState = stateDetector.detect(root?.packageName?.toString(), elements)
            val ranked = elementRanker.topElements(task.goal, elements)
            var plan = apiClient.getPlan(
                screenshot = screenshotProvider.capturePlaceholder(),
                uiElements = ranked,
                appState = appState,
                memory = memory.asPromptBlock(),
                goal = task.goal,
                complex = planner.isComplex(goal)
            )

            for (step in plan) {
                totalSteps++
                if (safetyLayer.needsConfirmation(step) && !onConfirm(step)) {
                    telemetry.record(TaskLog(goal, totalSteps, false, System.currentTimeMillis() - start, failures))
                    return@withContext ExecutionResult(false, "User denied risky action.")
                }
                val ok = performStep(step, onLog)
                if (!ok) {
                    failures++
                    onLog("Stuck on step: $step. Asking AI for recovery.")
                    val recovery = apiClient.getRecoveryAction(screenshotProvider.capturePlaceholder(), ranked, step)
                    if (!performStep(recovery, onLog)) {
                        telemetry.record(TaskLog(goal, totalSteps, false, System.currentTimeMillis() - start, failures))
                        return@withContext ExecutionResult(false, "Failed at ${task.goal}")
                    }
                }
            }
        }

        telemetry.record(TaskLog(goal, totalSteps, true, System.currentTimeMillis() - start, failures))
        ExecutionResult(true, "Completed")
    }

    private suspend fun performStep(step: Step, onLog: (String) -> Unit): Boolean {
        val service = AccessibilityAgentService.instance ?: return false
        val before = screenshotProvider.capturePlaceholder()
        val success = when (step.action) {
            ActionType.TAP -> tapWithRetries(service, step.targetText, onLog)
            ActionType.TYPE -> service.performType(step.value.orEmpty())
            ActionType.SWIPE, ActionType.SCROLL -> service.performSwipe(800, 1600, 800, 600)
            ActionType.BACK -> service.performBack()
            ActionType.HOME -> service.performHome()
            ActionType.WAIT -> true
        }
        waiter.waitUntil(10_000) { true }
        val after = screenshotProvider.capturePlaceholder()
        return success && actionVerifier.changed(before, after)
    }

    private suspend fun tapWithRetries(service: AccessibilityAgentService, targetText: String?, onLog: (String) -> Unit): Boolean {
        repeat(3) { attempt ->
            val node = findNodeByText(service.root(), targetText)
            val ok = if (node != null) {
                val b = android.graphics.Rect()
                node.getBoundsInScreen(b)
                service.performTap((b.left + b.right) / 2, (b.top + b.bottom) / 2)
            } else {
                false
            }
            if (ok) return true
            onLog("Tap retry ${attempt + 1}/3 for $targetText")
            waiter.waitUntil(2_000) { false }
        }
        return false
    }

    private fun findNodeByText(node: android.view.accessibility.AccessibilityNodeInfo?, targetText: String?): android.view.accessibility.AccessibilityNodeInfo? {
        node ?: return null
        if (targetText.isNullOrBlank()) return node
        val text = (node.text?.toString().orEmpty() + node.contentDescription?.toString().orEmpty()).lowercase()
        if (text.contains(targetText.lowercase())) return node
        for (i in 0 until node.childCount) {
            val found = findNodeByText(node.getChild(i), targetText)
            if (found != null) return found
        }
        return null
    }
}
