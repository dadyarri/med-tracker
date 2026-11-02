package dev.dadyarri.medtracker.meals

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MealPlanTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val date = LocalDate.of(2026, 11, 3)
    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    @Test fun mealScheduleIsTheSourceEvenWhenMealRemindersArePaused() {
        val setting = dev.dadyarri.medtracker.reminders.ReminderSetting(
            dev.dadyarri.medtracker.reminders.ReminderKind.BREAKFAST, paused = true, minute = 15)
        val cycle = GlucoseCycle(active = true, startDate = date.toString(), breakfastMinute = 600)
            .withMealTimes(listOf(setting))
        val task = GlucoseCyclePlan.tasks(cycle, date, emptyList(), zone).single()
        assertEquals(date.minusDays(1).atTime(23, 45).atZone(zone).toInstant().toEpochMilli(), task.at)
        val updated = cycle.withMealTimes(listOf(setting.copy(minute = 9 * 60)))
        assertEquals(date.atTime(8, 30).atZone(zone).toInstant().toEpochMilli(),
            GlucoseCyclePlan.tasks(updated, date, emptyList(), zone).single().at)
    }
    @Test fun calendarCycleAdvancesWithoutMealsAndResumesAtDayOne() {
        val cycle = GlucoseCycle(active = true, startDate = date.toString())
        assertEquals(1, cycle.day(date))
        assertEquals(2, cycle.day(date.plusDays(1)))
        assertEquals(3, cycle.day(date.plusDays(2)))
        assertEquals(1, cycle.day(date.plusDays(3)))
        assertNull(cycle.copy(active = false).day(date.plusDays(4)))
        assertEquals(1, cycle.copy(active = false).resume(date.plusDays(4)).day(date.plusDays(4)))
        assertNull(cycle.day(date.minusDays(1)))
    }
    @Test fun firstMealAnchorsAfterTaskAndMissingMealHasNoInventedDeadline() {
        val cycle = GlucoseCycle(active = true, startDate = date.toString())
        assertEquals(listOf("FASTING"), GlucoseCyclePlan.tasks(cycle, date, emptyList(), zone).map { it.food })
        val first = MealEvent("a", "BREAKFAST", at(date, 8), 1, 1)
        val second = MealEvent("b", "BREAKFAST", at(date, 10), 2, 2)
        val tasks = GlucoseCyclePlan.tasks(cycle, date, listOf(second, first), zone)
        assertEquals(at(date, 10), tasks.last().at)
        assertEquals("a", tasks.last().mealId)
        val corrected = first.copy(startedAt = at(date, 9))
        assertEquals(at(date, 11), GlucoseCyclePlan.tasks(cycle, date, listOf(second, corrected), zone).last().at)
        assertEquals(175, GlucoseCyclePlan.elapsedMinutes(first.startedAt + 175 * 60_000, first))
    }
    @Test fun lunchDinnerAndConfiguredDelayUseCorrectCalendarDay() {
        val cycle = GlucoseCycle(active = true, startDate = date.toString(), afterMinutes = 90)
        val lunch = MealEvent("l", "LUNCH", at(date.plusDays(1), 13), 1, 1)
        val result = GlucoseCyclePlan.tasks(cycle, date.plusDays(1), listOf(lunch), zone)
        assertEquals("BEFORE", result.first().food)
        assertEquals(lunch.startedAt + 90 * 60_000, result.last().at)
        assertEquals("DINNER", GlucoseCyclePlan.tasks(cycle, date.plusDays(2), emptyList(), zone).single().meal.name)
    }
}
