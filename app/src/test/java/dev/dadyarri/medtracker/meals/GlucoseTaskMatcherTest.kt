package dev.dadyarri.medtracker.meals

import dev.dadyarri.medtracker.measurements.GlucoseRecord
import dev.dadyarri.medtracker.reminders.ReminderOccurrence
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class GlucoseTaskMatcherTest {
    private val at = LocalDate.now().atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val meal = MealEvent("breakfast", "BREAKFAST", at - 120 * 60_000, 1, 1)
    private val task = ReminderOccurrence("GLUCOSE/date-after", "GLUCOSE", "r", "Глюкоза", at, at,
        at + 120 * 60_000, food = "AFTER", meal = "BREAKFAST", mealEventId = meal.id)
    private fun fact(id: String = "fact", time: Long = at) = GlucoseRecord(id, 570, "MMOL", "AFTER",
        null, "", time, 1, 1, "BREAKFAST")
    private fun match(rows: List<ReminderOccurrence>, facts: List<GlucoseRecord>, now: Long = at - 1,
                      desired: List<ReminderOccurrence> = listOf(task), meals: List<MealEvent> = listOf(meal)) =
        GlucoseTaskMatcher.reconcile(rows, desired, facts, meals, now)

    @Test fun independentRecordMatchesAndRepeatedReconcileIsIdempotent() {
        val record = fact(time = at + 55 * 60_000) // 175 minutes from actual meal.
        val result = match(listOf(task), listOf(record))
        assertEquals("COMPLETED", result.single().status)
        assertEquals("fact", result.single().recordId)
        assertNull(result.single().nextAt)
        assertEquals(result, match(result, listOf(record)))
    }
    @Test fun wrongContextMealAnchorAndTimeDoNotCompleteTask() {
        for (record in listOf(fact().copy(foodContext = "BEFORE"), fact().copy(meal = "DINNER"),
            fact().copy(mealEventId = "other"), fact(time = at - 31 * 60_000),
            fact(time = task.endAt + 1))) {
            assertEquals("WAITING", match(listOf(task), listOf(record)).single().status)
        }
    }
    @Test fun tiesAreDeterministicAndOneRecordNeverCompletesTwoTasks() {
        val other = task.copy(key = "GLUCOSE/another")
        val result = match(listOf(task, other), listOf(fact()), desired = listOf(task, other))
        assertEquals(1, result.count { it.status == "COMPLETED" })
        assertEquals(other.key, result.single { it.status == "COMPLETED" }.key)
    }
    @Test fun deletionReopensFutureButNeverReplaysPastAlarm() {
        val completed = match(listOf(task), listOf(fact()))
        val future = match(completed, emptyList()).single()
        assertEquals("WAITING", future.status)
        assertEquals(at, future.nextAt)
        val overdue = match(completed, emptyList(), now = at + 1).single()
        assertEquals("EXPIRED", overdue.status)
        assertNull(overdue.nextAt)
        assertNull(overdue.recordId)
    }
    @Test fun editingContextAndMovingMealRecomputesCompletionWithoutChangingFact() {
        val record = fact()
        val completed = match(listOf(task), listOf(record))
        assertEquals("WAITING", match(completed, listOf(record.copy(foodContext = "NONE"))).single().status)
        val moved = task.copy(dueAt = at + 120 * 60_000, nextAt = at + 120 * 60_000,
            endAt = at + 240 * 60_000, revision = "new")
        val result = match(completed, listOf(record), desired = listOf(moved),
            meals = listOf(meal.copy(startedAt = at)))
        assertEquals("WAITING", result.single().status)
        assertEquals(moved.dueAt, result.single().nextAt)
        assertEquals(at, record.measuredAt)
    }
    @Test fun removingMealClearsAfterCompletionAndPauseKeepsCompletedHistory() {
        val completed = match(listOf(task), listOf(fact()))
        assertEquals(completed, match(completed, listOf(fact()), desired = emptyList()))
        val deleted = match(completed, listOf(fact()), desired = emptyList(), meals = emptyList()).single()
        assertEquals("CANCELLED", deleted.status)
        assertNull(deleted.recordId)
    }
    @Test fun beforeAndFastingCannotMatchAnAlreadyStartedMeal() {
        val before = task.copy(key = "GLUCOSE/date-before", food = "FASTING", mealEventId = null)
        val record = fact().copy(foodContext = "FASTING", meal = null)
        assertEquals("WAITING", match(listOf(before), listOf(record), desired = listOf(before)).single().status)
        val breakfast = meal.copy(startedAt = at + 30 * 60_000)
        assertEquals("COMPLETED", match(listOf(before), listOf(record), desired = listOf(before),
            meals = listOf(breakfast)).single().status)
    }
    @Test fun explicitLateCompletionSurvivesUntilFactOrScheduleChanges() {
        val record = fact(time = task.endAt + 60 * 60_000)
        val explicit = task.copy(status = "COMPLETED", nextAt = null, recordId = record.id, completionSignature = GlucoseTaskMatcher.signature(record))
        assertEquals(explicit.copy(token = ""), match(listOf(explicit), listOf(record)).single())
        assertEquals("COMPLETED", match(listOf(explicit), listOf(record.copy(hundredths = 600, updatedAt = 2))).single().status)
        assertEquals("WAITING", match(listOf(explicit), listOf(record.copy(measuredAt = record.measuredAt + 1))).single().status)
        assertEquals("WAITING", match(listOf(explicit), listOf(record),
            desired = listOf(task.copy(revision = "changed"))).single().status)
    }
    @Test fun skippedTasksAreNotResurrectedByJournalFacts() {
        val skipped = task.copy(status = "CANCELLED", nextAt = null)
        assertEquals(skipped, match(listOf(skipped), listOf(fact())).single())
    }
}
