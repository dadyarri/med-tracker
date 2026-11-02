package dev.dadyarri.medtracker.measurements

import dev.dadyarri.medtracker.R

internal val MealType.icon: Int get() = when (this) {
    MealType.BREAKFAST -> R.drawable.ic_context_morning
    MealType.LUNCH -> R.drawable.ic_context_day
    MealType.DINNER -> R.drawable.ic_context_evening
    MealType.SNACK -> R.drawable.ic_meal
}
internal val FoodContext.icon: Int get() = when (this) {
    FoodContext.FASTING -> R.drawable.ic_context_fasting
    FoodContext.BEFORE -> R.drawable.ic_context_before_meal
    FoodContext.AFTER -> R.drawable.ic_context_after_meal
    FoodContext.NONE -> R.drawable.ic_context_unlinked
}
internal val GlucoseTag.icon: Int get() = when (this) {
    GlucoseTag.BEDTIME -> R.drawable.ic_context_night
    GlucoseTag.UNPLANNED -> R.drawable.ic_context_unplanned
}
