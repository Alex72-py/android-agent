package com.aquib.aiagent.agent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aquib.aiagent.accessibility.AccessibilityAgentService
import com.aquib.aiagent.ai.GeminiApiClient
import com.aquib.aiagent.memory.TaskMemory
import com.aquib.aiagent.models.ActionType
import com.aquib.aiagent.models.ExecutionResult
import com.aquib.aiagent.models.Step
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
    suspend fun executeGoal(
        goal: String,
        onLog: (String) -> Unit,
        onConfirm: suspend (Step) -> Boolean
    ): ExecutionResult = withContext(Dispatchers.Default) {
        val start = System.currentTimeMillis()
        var totalSteps = 0
        var failures = 0
        val subTasks = planner.build(goal)

        for ((index, task) in subTasks.withIndex()) {
            onLog("Stage ${index + 1}/${subTasks.size}: ${task.goal}")
            val initialPlan = runCatching { buildPlan(task.goal, goal) }
                .getOrElse { error ->
                    val msg = error.message ?: "Planning failed"
                    telemetry.record(TaskLog(goal, totalSteps, false, System.currentTimeMillis() - start, failures))
                    return@withContext ExecutionResult(false, msg)
                }

            val stageResult = executePlan(initialPlan, goal, onLog, onConfirm)
            totalSteps += stageResult.steps
            failures += stageResult.failures

            if (!stageResult.success) {
                telemetry.record(TaskLog(goal, totalSteps, false, System.currentTimeMillis() - start, failures))
                return@withContext ExecutionResult(false, stageResult.message)
            }
        }

        telemetry.record(TaskLog(goal, totalSteps, true, System.currentTimeMillis() - start, failures))
        ExecutionResult(true, "Completed")
    }

    private suspend fun buildPlan(subTaskGoal: String, fullGoal: String): List<Step> {
        val root = AccessibilityAgentService.instance?.root()
        val elements = uiExtractor.extract(root)
        val state = stateDetector.detect(root?.packageName?.toString(), elements)
        val ranked = elementRanker.topElements(subTaskGoal, elements)
        return apiClient.getPlan(
            screenshot = screenshotProvider.captureSemanticFrame(root),
            uiElements = ranked,
            appState = state,
            memory = memory.asPromptBlock(),
            goal = subTaskGoal,
            complex = planner.isComplex(fullGoal)
        )
    }

    private data class PlanExecution(val success: Boolean, val message: String, val steps: Int, val failures: Int)

    private suspend fun executePlan(
        plan: List<Step>,
        fullGoal: String,
        onLog: (String) -> Unit,
        onConfirm: suspend (Step) -> Boolean
    ): PlanExecution {
        var steps = 0
        var failures = 0

        for (step in plan) {
            steps++
            if (safetyLayer.needsConfirmation(step) && !onConfirm(step)) {
                return PlanExecution(false, "User denied risky action", steps, failures)
            }

            val ok = performStep(step, onLog)
            if (ok) continue

            failures++
            onLog("Step failed: $step. Requesting recovery action.")
            val root = AccessibilityAgentService.instance?.root()
            val recovery = runCatching {
                apiClient.getRecoveryAction(
                    screenshot = screenshotProvider.captureSemanticFrame(root),
                    uiElements = elementRanker.topElements(
                        "Recover from failed step",
                        uiExtractor.extract(root)
                    ),
                    failedStep = step
                )
            }.getOrElse {
                return PlanExecution(false, "Recovery planning failed", steps, failures)
            }

            steps++
            val recoveryOk = performStep(recovery, onLog)
            if (!recoveryOk) {
                failures++
                val fallbackPlan = runCatching { buildPlan("Recover from failed step: $step", fullGoal) }
                    .getOrElse { return PlanExecution(false, "Recovery failed", steps, failures) }

                for (fallbackStep in fallbackPlan) {
                    steps++
                    if (!performStep(fallbackStep, onLog)) {
                        failures++
                        return PlanExecution(false, "Recovery failed at $fallbackStep", steps, failures)
                    }
                }
            }
        }
        return PlanExecution(true, "OK", steps, failures)
    }

    private suspend fun performStep(step: Step, onLog: (String) -> Unit): Boolean {
        val service = AccessibilityAgentService.instance ?: return false
        val before = screenshotProvider.captureSemanticFrame(service.root())

        val actionSucceeded = when (step.action) {
            ActionType.TAP -> tapWithRetries(service, step.targetText, onLog)
            ActionType.TYPE -> service.performType(step.value.orEmpty())
            ActionType.SWIPE, ActionType.SCROLL -> service.performSwipe(800, 1600, 800, 600)
            ActionType.BACK -> service.performBack()
            ActionType.HOME -> service.performHome()
            ActionType.WAIT -> true
        }

        val settled = waiter.waitForSettle(snapshot = {
            screenshotProvider.captureSemanticFrame(service.root()).contentHashCode()
        }, timeoutMs = if (step.action == ActionType.TYPE) 12_000 else 8_000)

        val after = screenshotProvider.captureSemanticFrame(service.root())
        val changed = if (step.action == ActionType.WAIT) true else actionVerifier.changed(before, after)
        return actionSucceeded && settled && changed
    }

    private suspend fun tapWithRetries(
        service: AccessibilityAgentService,
        targetText: String?,
        onLog: (String) -> Unit
    ): Boolean {
        repeat(3) { attempt ->
            val node = findNodeByText(service.root(), targetText)
            val tapped = node?.let {
                val bounds = Rect()
                it.getBoundsInScreen(bounds)
                service.performTap((bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2)
            } ?: false

            if (tapped) return true
            onLog("Tap retry ${attempt + 1}/3 for target: ${targetText.orEmpty()}")
            waiter.waitUntil(timeoutMs = 2_000, pollMs = 250) {
                findNodeByText(service.root(), targetText) != null
            }
        }
        return false
    }

    private fun findNodeByText(node: AccessibilityNodeInfo?, targetText: String?): AccessibilityNodeInfo? {
        node ?: return null
        if (targetText.isNullOrBlank()) return if (node.isClickable) node else null

        val text = listOf(node.text?.toString(), node.contentDescription?.toString())
            .joinToString(" ")
            .lowercase()
        if (node.isClickable && text.contains(targetText.lowercase())) return node

        for (i in 0 until node.childCount) {
            val found = findNodeByText(node.getChild(i), targetText)
            if (found != null) return found
        }
        return null
    }
}
