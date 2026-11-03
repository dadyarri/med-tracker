package dev.dadyarri.medtracker.medications

import dev.dadyarri.medtracker.meals.MealEvent
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MedicationScheduleTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val date = LocalDate.of(2026, 11, 3)
    private fun at(day: LocalDate = date, hour: Int = 0) = day.atTime(hour, 0).atZone(zone).toInstant()
    private val regimen = MedicationRegimen("r", "m", "CONTINUOUS", at().toEpochMilli(), createdAt = 1)
    private val version = RegimenVersion("v1", "r", at().toEpochMilli(), "1", "табл.", "DAILY",
        minutes = "480,1200", anchorDate = date.toString())
    private fun tasks(reg: MedicationRegimen = regimen, versions: List<RegimenVersion> = listOf(version),
                      facts: List<MedicationIntake> = emptyList(), meals: List<MealEvent> = emptyList(),
                      from: Instant = at(), until: Instant = at(date.plusDays(3))) =
        MedicationSchedule.tasks(reg, versions, facts, meals, emptyList(), from, until, zone)
    private fun fact(id: String, hour: Int, created: Long) = MedicationIntake(id, "m", "r", "v1", "1", "табл.",
        at(hour = hour).toEpochMilli(), created, created)
    @Test fun dailyWeekdayAndCalendarDaySchedulesAreDistinct() {
        assertEquals(6, tasks().size)
        assertEquals(2, tasks(versions = listOf(version.copy(mode = "WEEKDAYS", weekdays = "2,4", minutes = "480")),
            until = at(date.plusDays(4))).size)
        val everyTwo = tasks(versions = listOf(version.copy(mode = "DAYS", everyDays = 2, minutes = "480")),
            until = at(date.plusDays(5)))
        assertEquals(listOf(date, date.plusDays(2), date.plusDays(4)), everyTwo.map { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() })
    }
    @Test fun intervalHasNoInventedFirstAnchorAndUsesTakenAtNotCreationOrder() {
        val interval = version.copy(mode = "HOURS", everyHours = 8)
        assertTrue(tasks(versions = listOf(interval)).isEmpty())
        val newestEntered = fact("entered-later", 8, 900)
        val latestActual = fact("actual-last", 9, 100)
        val next = tasks(versions = listOf(interval), facts = listOf(newestEntered, latestActual), from = at(hour = 10)).single()
        assertEquals(at(hour = 17).toEpochMilli(), next.at)
        assertEquals(at(hour = 16).toEpochMilli(), tasks(versions = listOf(interval),
            facts = listOf(newestEntered), from = at(hour = 10)).single().at)
        assertEquals(next.at, tasks(versions = listOf(interval), facts = listOf(newestEntered.copy(takenAt = at(hour = 7).toEpochMilli()),
            latestActual), from = at(hour = 10)).single().at)
    }
    @Test fun versionBoundarySelectsCompleteSnapshotWithoutOverlappingDoses() {
        val changed = version.copy(id = "v2", dose = "0.5", effectiveAt = at(date.plusDays(1), 12).toEpochMilli())
        val rows = tasks(versions = listOf(changed, version), from = at(date.plusDays(1)), until = at(date.plusDays(2)))
        assertEquals(listOf("1", "0.5"), rows.map { it.dose })
        assertEquals(listOf("v1", "v2"), rows.map { it.versionId })
        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }
    @Test fun courseEndAndManualFinishExcludeFutureTasks() {
        val course = regimen.copy(kind = "COURSE", endsAt = at(date.plusDays(1)).toEpochMilli())
        assertEquals(2, tasks(course).size)
        assertEquals(1, tasks(course.copy(completedAt = at(hour = 12).toEpochMilli())).size)
        assertTrue(tasks(regimen.copy(completedAt = regimen.startsAt - 1)).isEmpty())
    }
    @Test fun foodOffsetFallsBackToScheduleAndUsesActualFirstMealAfterwards() {
        val food = version.copy(food = "AFTER", foodOffset = true, meal = "LUNCH", offsetMinutes = 30)
        assertEquals(at(hour = 13).plusSeconds(1800).toEpochMilli(), tasks(versions = listOf(food)).first().at)
        val first = MealEvent("first", "LUNCH", at(hour = 12).toEpochMilli(), 100, 100)
        val second = first.copy(id = "second", startedAt = at(hour = 14).toEpochMilli())
        assertEquals(at(hour = 12).plusSeconds(1800).toEpochMilli(),
            tasks(versions = listOf(food), meals = listOf(second, first)).first().at)
        val before = food.copy(food = "BEFORE")
        assertEquals(at(hour = 13).minusSeconds(1800).toEpochMilli(),
            tasks(versions = listOf(before), meals = listOf(first)).first().at)
        assertEquals(at(hour = 8).toEpochMilli(), tasks(versions = listOf(food.copy(foodOffset = false))).first().at)
    }
    @Test fun onceAndPrnDoNotCreateDailyOrIntervalTasks() {
        val once = version.copy(mode = "ONCE", onceAt = at(hour = 9).toEpochMilli())
        assertEquals(1, tasks(regimen.copy(kind = "ONCE"), listOf(once)).size)
        assertTrue(tasks(regimen.copy(kind = "PRN"), listOf(version.copy(mode = "PRN"))).isEmpty())
    }
    @Test fun calendarDaysRespectDstRatherThanAddingTwentyFourHours() {
        val berlin = ZoneId.of("Europe/Berlin")
        val start = LocalDate.of(2026, 3, 28).atStartOfDay(berlin).toInstant()
        val reg = regimen.copy(startsAt = start.toEpochMilli())
        val v = version.copy(effectiveAt = reg.startsAt, mode = "DAYS", everyDays = 1, minutes = "480", anchorDate = "2026-03-28")
        val result = MedicationSchedule.tasks(reg, listOf(v), emptyList(), emptyList(), emptyList(),
            start, start.plusSeconds(3 * 86400), berlin)
        assertTrue(result.all { Instant.ofEpochMilli(it.at).atZone(berlin).hour == 8 })
        assertEquals(23, Duration.between(Instant.ofEpochMilli(result[0].at), Instant.ofEpochMilli(result[1].at)).toHours())
    }
}
