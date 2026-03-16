package com.aquib.aiagent.memory

class TaskMemory {
    private val values = linkedMapOf<String, String>()

    fun put(key: String, value: String) {
        values[key] = value
    }

    fun asPromptBlock(): String = values.entries.joinToString("\n") { "${it.key}: ${it.value}" }

    fun snapshot(): Map<String, String> = values.toMap()
}
