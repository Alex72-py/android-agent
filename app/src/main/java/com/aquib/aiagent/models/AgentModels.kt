package com.aquib.aiagent.models

data class UiElement(
    val text: String,
    val contentDescription: String,
    val resourceId: String,
    val className: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val clickable: Boolean
)

data class ScreenSnapshot(
    val width: Int,
    val height: Int,
    val bytes: ByteArray
)

enum class ActionType {
    TAP, TYPE, SWIPE, SCROLL, BACK, HOME, WAIT
}

data class Step(
    val action: ActionType,
    val targetText: String? = null,
    val value: String? = null,
    val direction: String? = null,
    val confidence: Float = 1f
)

data class SubTask(
    val goal: String,
    val successSignal: String
)

data class ExecutionResult(
    val success: Boolean,
    val message: String
)
