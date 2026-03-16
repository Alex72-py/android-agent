package com.aquib.aiagent.ui

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aquib.aiagent.models.UiElement

class UIExtractor {
    fun extract(root: AccessibilityNodeInfo?): List<UiElement> {
        if (root == null) return emptyList()
        val list = mutableListOf<UiElement>()
        walk(root, list)
        return list
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<UiElement>) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        out += UiElement(
            text = node.text?.toString().orEmpty(),
            contentDescription = node.contentDescription?.toString().orEmpty(),
            resourceId = node.viewIdResourceName.orEmpty(),
            className = node.className?.toString().orEmpty(),
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
            clickable = node.isClickable
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, out) }
        }
    }
}
