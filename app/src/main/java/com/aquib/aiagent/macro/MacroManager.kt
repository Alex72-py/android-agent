package com.aquib.aiagent.macro

import com.aquib.aiagent.models.Step

class MacroManager {
    private val macros = mutableMapOf<String, List<Step>>()

    fun record(name: String, steps: List<Step>) {
        macros[name] = steps
    }

    fun replay(name: String): List<Step> = macros[name].orEmpty()
}
