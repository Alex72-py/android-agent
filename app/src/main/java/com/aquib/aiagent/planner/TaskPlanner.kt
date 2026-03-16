package com.aquib.aiagent.planner

import com.aquib.aiagent.models.SubTask

class TaskPlanner {
    fun build(goal: String): List<SubTask> {
        val g = goal.lowercase()
        return when {
            g.contains("amazon") -> listOf(
                SubTask("Open Amazon and search for target product", "Search results visible"),
                SubTask("Filter and select best matching product", "Product page opened"),
                SubTask("Add selected product to cart", "Cart count updated"),
                SubTask("Proceed to checkout and open order screen", "Order screen visible")
            )
            g.contains("swiggy") || g.contains("food") -> listOf(
                SubTask("Open food app and search restaurant/item", "Restaurant listing visible"),
                SubTask("Add item and continue to checkout", "Checkout page visible"),
                SubTask("Reach payment screen", "Payment methods visible")
            )
            else -> listOf(SubTask(goal, "Goal result visible"))
        }
    }

    fun isComplex(goal: String): Boolean {
        val g = goal.lowercase()
        return listOf("amazon", "swiggy", "youtube", "payment", "checkout").count { g.contains(it) } >= 2
    }
}
