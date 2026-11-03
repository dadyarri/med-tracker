package dev.dadyarri.medtracker.reminders

import android.app.Application
import android.content.Intent
import dev.dadyarri.medtracker.measurements.*
import dev.dadyarri.medtracker.pressure.PressureDraft
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReminderEntryTest {
    private val now = Instant.parse("2026-11-03T09:12:34Z").toEpochMilli()
    private fun row(source: String, at: Long = now) =
        ReminderOccurrence("$source/date", source, "r", source, at, at, at + 7_200_000)
    @Test fun timelineIncludesAllSupportedPlannedTypesAndRealRecordsOnlyForToday() {
        val zone = ZoneId.of("UTC")
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val plans = listOf("PRESSURE", "WEIGHT", "GLUCOSE", "BREAKFAST", "LUNCH", "DINNER", "SNACK").map(::row)
        val old = row("PRESSURE", now - 86_400_000).copy(key = "yesterday")
        val cancelled = row("PRESSURE").copy(key = "cancelled", status = "CANCELLED")
        val completed = row("WEIGHT").copy(key = "completed", status = "COMPLETED")
        val fact = JournalEntry.Weight(WeightRecord("fact", 700, null, "", now + 1234, now, now))
        val timeline = todayTimeline(listOf(fact), plans + listOf(old, cancelled, completed), date, zone)
        assertEquals(8, timeline.size)
        assertEquals(fact, timeline.first().record)
        assertEquals(7, timeline.count { it.planned != null })
        assertEquals(8, timeline.map { it.key }.distinct().size)
    }
    @Test fun prefillUsesCurrentActualTimeAndStructuredFoodMetadata() {
        val row = row("GLUCOSE").copy(food = "AFTER", meal = "BREAKFAST", mealEventId = "meal")
        val draft = row.measurementDraft(now)
        assertEquals(MeasurementType.GLUCOSE, draft.type)
        assertEquals(FoodContext.AFTER, draft.food)
        assertEquals(MealType.BREAKFAST, draft.meal)
        assertEquals("meal", draft.mealEventId)
        assertEquals(now, draft.instant())
        assertEquals("", draft.value)
        assertEquals(now.toString(), row("PRESSURE").pressureInitial(now)[6])
        assertEquals(PressureDraft.format(now), row("PRESSURE").pressureInitial(now)[3])
    }
    @Test fun missingMalformedStaleAndCompletedRequestsAreRejected() {
        assertNull(ReminderEntryRequest.from(Intent()))
        assertNull(ReminderEntryRequest.from(Intent().putExtra(ReminderAlarmController.KEY, "key")))
        assertNull(ReminderEntryRequest.from(Intent().putExtra(dev.dadyarri.medtracker.alarm.AlarmController.TOKEN, "")))
        val row = row("PRESSURE")
        val request = ReminderEntryRequest(row.key, row.revision, null)
        assertTrue(request.accepts(row))
        assertFalse(request.accepts(row.copy(revision = "new")))
        assertFalse(request.accepts(row.copy(status = "COMPLETED")))
        assertFalse(request.accepts(row.copy(status = "CANCELLED")))
        assertTrue(request.accepts(row.copy(status = "EXPIRED")))
        val group = ReminderEntryRequest(null, null, "group")
        assertTrue(group.accepts(row.copy(status = "RINGING", token = "group")))
        assertFalse(group.accepts(row.copy(status = "WAITING", token = "group")))
        assertFalse(group.accepts(row.copy(status = "RINGING", token = "old")))
    }
    @Test fun notificationTapTargetsEntryWhileFullscreenTargetsAlarmAndActionsStay() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val row = row("PRESSURE").copy(status = "RINGING", token = "token")
        val notification = ReminderNotifications(context).build(listOf(row), true)
        val intent = org.robolectric.Shadows.shadowOf(notification.contentIntent).savedIntent
        assertEquals(ReminderEntryActivity::class.java.name, intent.component!!.className)
        assertTrue(notification.contentIntent.isImmutable)
        assertTrue(ReminderEntryRequest.from(intent)!!.accepts(row))
        assertEquals(listOf("Пропустить", "Без звука", "Отложить 5 мин"), notification.actions.map { it.title.toString() })
        val group = ReminderNotifications(context).build(listOf(row, row("WEIGHT").copy(status = "RINGING", token = "token")), false)
        val groupIntent = org.robolectric.Shadows.shadowOf(group.contentIntent).savedIntent
        assertEquals(ReminderEntryActivity::class.java.name, groupIntent.component!!.className)
        assertNull(groupIntent.getStringExtra(ReminderAlarmController.KEY))
        assertEquals("token", groupIntent.getStringExtra(dev.dadyarri.medtracker.alarm.AlarmController.TOKEN))
    }
}
