package com.aquib.aiagent.safety

import com.aquib.aiagent.models.Step

class SafetyLayer {
    private val riskyTerms = listOf("pay", "payment", "delete", "factory reset", "send money")

    fun needsConfirmation(step: Step): Boolean {
        val payload = "${step.targetText.orEmpty()} ${step.value.orEmpty()}".lowercase()
        return riskyTerms.any { payload.contains(it) }
    }
}
