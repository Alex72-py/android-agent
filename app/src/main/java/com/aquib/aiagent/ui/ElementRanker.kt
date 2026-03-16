package com.aquib.aiagent.ui

import com.aquib.aiagent.models.UiElement

class ElementRanker {
    fun topElements(goal: String, elements: List<UiElement>, limit: Int = 12): List<UiElement> {
        val terms = goal.lowercase().split(" ").filter { it.isNotBlank() }
        return elements
            .sortedByDescending { element ->
                var score = 0
                val hay = "${element.text} ${element.contentDescription} ${element.resourceId}".lowercase()
                terms.forEach { if (hay.contains(it)) score += 3 }
                if (element.clickable) score += 1
                score
            }
            .take(limit)
    }
}
