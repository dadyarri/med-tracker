package dev.dadyarri.medtracker.meals

import dev.dadyarri.medtracker.measurements.GlucoseRecord
import dev.dadyarri.medtracker.reminders.ReminderOccurrence
import kotlin.math.abs

/** A journal fact closes at most one cycle task; explicit links take precedence. */
internal object GlucoseTaskMatcher {
    fun signature(record: GlucoseRecord) =
        "${record.measuredAt}/${record.foodContext}/${record.meal.orEmpty()}/${record.mealEventId.orEmpty()}"
    fun reconcile(rows: List<ReminderOccurrence>, desired: List<ReminderOccurrence>,
                  records: List<GlucoseRecord>, meals: List<MealEvent>, now: Long): List<ReminderOccurrence> {
        val wanted = desired.filter { it.source == "GLUCOSE" }.associateBy { it.key }
        val facts = records.associateBy { it.id }
        val used = mutableSetOf<String>()
        fun reopened(row: ReminderOccurrence): ReminderOccurrence {
            val candidate = wanted[row.key]
            return if (candidate != null && candidate.dueAt > now) candidate
                else (candidate ?: row).copy(status = if (candidate == null) "CANCELLED" else "EXPIRED",
                    nextAt = null, token = "", recordId = null, completionSignature = null)
        }
        val result = rows.associateBy { it.key }.toMutableMap()
        rows.filter { it.source == "GLUCOSE" && it.status == "COMPLETED" }.sortedBy { it.key }.forEach { row ->
            val candidate = wanted[row.key]
            val record = facts[row.recordId]
            // Pausing a plan keeps completed history. Removing its fact still removes completion.
            val anchorExists = row.food != "AFTER" || row.mealEventId == null || meals.any { it.id == row.mealEventId }
            val explicit = record != null && row.completionSignature == signature(record) &&
                (candidate == null || candidate.revision == row.revision)
            val matches = record != null && anchorExists && (explicit || matches(candidate ?: row, record, meals))
            result[row.key] = if (matches && used.add(record.id)) {
                (candidate ?: row).copy(status = "COMPLETED", nextAt = null, token = "", recordId = record.id,
                    completionSignature = row.completionSignature.takeIf { explicit })
            } else reopened(row)
        }
        records.sortedWith(compareBy<GlucoseRecord> { it.measuredAt }.thenBy { it.createdAt }.thenBy { it.id })
            .filterNot { it.id in used }.forEach { record ->
                val row = result.values.filter {
                    it.source == "GLUCOSE" && it.key in wanted && it.canRecord && matches(it, record, meals)
                }.minWithOrNull(compareBy<ReminderOccurrence> { abs(it.dueAt - record.measuredAt) }.thenBy { it.key })
                if (row != null) {
                    used.add(record.id)
                    result[row.key] = row.copy(status = "COMPLETED", nextAt = null, token = "", recordId = record.id)
                }
            }
        return rows.map { result.getValue(it.key) }
    }

    private fun matches(row: ReminderOccurrence, record: GlucoseRecord, meals: List<MealEvent>): Boolean {
        if (record.foodContext != row.food) return false
        if (record.meal != row.meal && !(record.meal == null && row.food == "FASTING")) return false
        if (record.mealEventId != null) {
            val meal = meals.find { it.id == record.mealEventId } ?: return false
            if (meal.type != row.meal) return false
            if (row.food == "AFTER" && record.mealEventId != row.mealEventId) return false
        }
        if (record.measuredAt !in (row.dueAt - 30 * 60_000L)..row.endAt) return false
        if (row.food == "AFTER") {
            val meal = meals.find { it.id == row.mealEventId } ?: return false
            return record.measuredAt >= meal.startedAt
        }
        // A before/fasting fact must precede the first meal of this task's calendar date.
        val zone = java.time.ZoneId.systemDefault()
        val date = java.time.Instant.ofEpochMilli(row.dueAt + 30 * 60_000L).atZone(zone).toLocalDate()
        val first = meals.filter {
            it.type == row.meal && java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == date
        }.minOfOrNull { it.startedAt }
        return first == null || record.measuredAt <= first
    }
}
