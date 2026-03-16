package com.aquib.aiagent.state

import com.aquib.aiagent.models.UiElement

class AppStateDetector {
    fun detect(packageName: String?, elements: List<UiElement>): String {
        val app = packageName?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() } ?: "Unknown"
        val labels = elements.asSequence().map { it.text.lowercase() }.toList()
        val screen = when {
            labels.any { it.contains("cart") } -> "Cart"
            labels.any { it.contains("payment") || it.contains("upi") || it.contains("cash on delivery") } -> "Payment Screen"
            labels.any { it.contains("search") } -> "Search"
            else -> "General"
        }
        return "$app › $screen"
    }
}
