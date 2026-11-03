package dev.dadyarri.medtracker.meals

import dev.dadyarri.medtracker.measurements.MealType

data class CycleDay(val meal: MealType, val before: String, val after: Boolean) {
    fun encode() = "${meal.name}/$before/${if (after) "AFTER" else "NONE"}"
    companion object {
        fun parse(value: String): CycleDay? = runCatching {
            val parts = value.split('/')
            require(parts.size == 3)
            val meal = MealType.valueOf(parts[0])
            require(meal != MealType.SNACK && parts[1] in listOf("FASTING", "BEFORE", "NONE"))
            require(parts[2] in listOf("AFTER", "NONE"))
            require(parts[1] != "NONE" || parts[2] != "NONE")
            CycleDay(meal, parts[1], parts[2] == "AFTER")
        }.getOrNull()
    }
}
