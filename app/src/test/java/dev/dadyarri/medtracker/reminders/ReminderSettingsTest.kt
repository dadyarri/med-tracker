package dev.dadyarri.medtracker.reminders

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderSettingsTest {
    private val zone = ZoneId.of("Europe/Moscow")
    @Test fun dailySchedulesResumeAtChosenTimeWithoutChangingFrequency() {
        val now = Instant.parse("2026-11-03T06:00:00Z")
        for (kind in ReminderKind.entries.filter { it != ReminderKind.WEIGHT }) {
            val setting = ReminderSetting(kind, paused = false, minute = 8 * 60)
            assertEquals(Instant.parse("2026-11-04T05:00:00Z"), setting.next(now, zone))
            assertNull(setting.copy(paused = true).next(now, zone))
            assertEquals(setting.next(now, zone), setting.copy(paused = true).copy(paused = false).next(now, zone))
        }
    }
    @Test fun weightUsesCalendarWeeksFromAnchorRatherThanLastMeasurement() {
        val setting = ReminderSetting(ReminderKind.WEIGHT, false, 8 * 60, 2, LocalDate.of(2026, 11, 3))
        assertEquals(Instant.parse("2026-11-17T05:00:00Z"), setting.next(Instant.parse("2026-11-03T06:00:00Z"), zone))
        assertEquals(Instant.parse("2026-11-03T05:00:00Z"), setting.next(Instant.parse("2026-11-03T04:00:00Z"), zone))
        assertEquals(Instant.parse("2026-11-17T05:00:00Z"), setting.next(Instant.parse("2026-11-10T04:00:00Z"), zone))
        assertFalse(setting.copy(weeks = 0).valid())
        assertFalse(setting.copy(minute = 1440).valid())
    }
    @Test fun dailyScheduleKeepsLocalClockAcrossDaylightSaving() {
        val zone = ZoneId.of("Europe/Berlin")
        val setting = ReminderSetting(ReminderKind.PRESSURE, false, 8 * 60)
        assertEquals(Instant.parse("2026-10-25T07:00:00Z"), setting.next(Instant.parse("2026-10-24T08:00:00Z"), zone))
    }
}
