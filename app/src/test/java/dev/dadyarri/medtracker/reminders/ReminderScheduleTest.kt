package dev.dadyarri.medtracker.reminders

import dev.dadyarri.medtracker.meals.*
import dev.dadyarri.medtracker.measurements.MealType
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderScheduleTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val now = LocalDate.of(2026, 11, 3).atTime(7, 0).atZone(zone).toInstant()
    private val daily = ReminderSetting(ReminderKind.PRESSURE, paused = false, minute = 480)
    private fun desired(settings: List<ReminderSetting> = listOf(daily), cycle: GlucoseCycle? = null,
                        meals: List<MealEvent> = emptyList()) = ReminderSchedule.desired(settings, cycle, meals, now, zone)
    @Test fun enableDoesNotSchedulePastAndReconcileIsIdempotent() {
        val result = ReminderSchedule.merge(emptyList(), desired(), now.toEpochMilli())
        assertTrue(result.all { it.dueAt >= now.toEpochMilli() })
        assertEquals(result, ReminderSchedule.merge(result, desired(), now.toEpochMilli()))
    }
    @Test fun pauseCancelsAndResumeRestoresFutureButSkipDoesNotReappear() {
        val planned = ReminderSchedule.merge(emptyList(), desired(), now.toEpochMilli())
        val paused = ReminderSchedule.merge(planned, emptyList(), now.toEpochMilli())
        assertTrue(paused.none { it.active })
        val resumed = ReminderSchedule.merge(paused, desired(), now.toEpochMilli())
        assertEquals(planned, resumed)
        val skipped = resumed.map { it.copy(status = "CANCELLED", nextAt = null) }
        assertTrue(ReminderSchedule.merge(skipped, desired(), now.toEpochMilli()).none { it.active })
    }
    @Test fun changingTimeReplacesFutureOccurrenceAndInvalidatesOld() {
        val old = ReminderSchedule.merge(emptyList(), desired(), now.toEpochMilli())
        val changed = desired(listOf(daily.copy(minute = 600)))
        val merged = ReminderSchedule.merge(old, changed, now.toEpochMilli())
        assertTrue(merged.filter { it.active }.all { it.revision == "600" })
        assertEquals(10, Instant.ofEpochMilli(merged.first { it.active }.dueAt).atZone(zone).hour)
    }
    @Test fun recoveryExpiresMissedWithoutRingingAndRetainsFuture() {
        val all = ReminderSchedule.merge(emptyList(), desired(), now.toEpochMilli())
        val later = now.plusSeconds(2 * 3600).toEpochMilli()
        val recovered = ReminderSchedule.merge(all, desired(), later, true)
        assertEquals("EXPIRED", recovered.first().status)
        assertTrue(recovered.drop(1).all { it.status == "WAITING" })
    }
    @Test fun cycleUsesMealScheduleEvenWhenPausedAndAfterUsesActualFirstMeal() {
        val settings = listOf(ReminderSetting(ReminderKind.BREAKFAST, paused = true, minute = 540))
        val cycle = GlucoseCycle(active = true, startDate = "2026-11-03")
        val before = desired(settings, cycle)
        assertEquals(8, Instant.ofEpochMilli(before.first().dueAt).atZone(zone).hour)
        assertEquals(30, Instant.ofEpochMilli(before.first().dueAt).atZone(zone).minute)
        assertFalse(before.any { it.key.endsWith("after") })
        val at = now.plusSeconds(3 * 3600).toEpochMilli()
        val meal = MealEvent("meal", MealType.BREAKFAST.name, at, at, at)
        val after = desired(settings, cycle, listOf(meal)).single { it.key == "GLUCOSE/2026-11-03-after" }
        assertEquals(at + 120 * 60_000L, after.dueAt)
        assertFalse(desired(settings, cycle.copy(active = false), listOf(meal)).any { it.source == "GLUCOSE" })
    }
    @Test fun weeklyWeightStaysAnchoredAndDoesNotBecomeDaily() {
        val value = ReminderSetting(ReminderKind.WEIGHT, paused = false, minute = 480, weeks = 2,
            anchor = LocalDate.of(2026, 11, 3))
        val result = desired(listOf(value))
        assertEquals(14, Duration.between(Instant.ofEpochMilli(result[0].dueAt),
            Instant.ofEpochMilli(result[1].dueAt)).toDays())
    }
}
