package dev.dadyarri.medtracker.medications

import dev.dadyarri.medtracker.meals.MealEvent
import dev.dadyarri.medtracker.reminders.ReminderSchedule
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MedicationRemindersTest {
    private val zone = ZoneId.of("Europe/Moscow")
    private val date = LocalDate.of(2026, 11, 3)
    private fun at(hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant()
    private val drug = Medication("m", "Препарат")
    private val regimen = MedicationRegimen("r", "m", "CONTINUOUS", at(0).toEpochMilli(), createdAt = 1)
    private val version = RegimenVersion("v", "r", regimen.startsAt, "0.5", "табл.", "DAILY",
        minutes = "600,615", anchorDate = date.toString())
    private fun desired(data: MedicationData = MedicationData(listOf(drug), listOf(regimen), listOf(version)),
                        now: Instant = at(9), meals: List<MealEvent> = emptyList()) =
        MedicationReminders.desired(data, meals, emptyList(), now, zone)
    @Test fun payloadIdentifiesSnapshotAndInactiveOrPrnAssignmentsHaveNoFutureTasks() {
        val rows = desired()
        val row = rows.first { it.dueAt == at(10).toEpochMilli() }
        assertEquals("MEDICATION", row.source)
        assertEquals("m", row.medicationId); assertEquals("r", row.regimenId); assertEquals("v", row.versionId)
        assertEquals("0.5", row.dose); assertEquals("табл.", row.unit)
        assertTrue(desired(MedicationData(listOf(drug.copy(archived = true)), listOf(regimen), listOf(version))).isEmpty())
        val prn = MedicationData(listOf(drug), listOf(regimen.copy(kind = "PRN")), listOf(version.copy(mode = "PRN")))
        assertTrue(desired(prn).isEmpty())
        val finished = desired(MedicationData(listOf(drug), listOf(regimen.copy(completedAt = at(8).toEpochMilli())), listOf(version)))
        assertTrue(finished.none { it.dueAt >= at(9).toEpochMilli() })
    }
    @Test fun intervalUsesActualAnchorEvenWhenOccurrenceWindowStartsYesterday() {
        val data = MedicationData(listOf(drug), listOf(regimen), listOf(version.copy(mode = "HOURS")))
        assertTrue(desired(data).isEmpty())
        val fact = MedicationIntake("i", "m", "r", "v", "0.5", "табл.", at(8).toEpochMilli(), 1, 1)
        assertEquals(at(16).toEpochMilli(), desired(data.copy(intakes = listOf(fact))).single().dueAt)
        val overdue = desired(data.copy(intakes = listOf(fact)), at(17)).single()
        assertEquals(at(16).toEpochMilli(), overdue.dueAt)
        assertEquals("EXPIRED", ReminderSchedule.merge(emptyList(), listOf(overdue), at(17).toEpochMilli()).single().status)
    }
    @Test fun oneFactClosesOneTaskAndChangingOrDeletingItReopensOnlyFutureTasks() {
        val rows = desired()
        val fact = MedicationIntake("i", "m", "r", "v", "0.25", "табл.", at(10).toEpochMilli() + 60_000, 1, 1)
        val completed = MedicationReminders.reconcile(rows, rows, listOf(fact), at(9).toEpochMilli())
        assertEquals(1, completed.count { it.status == "COMPLETED" })
        val old = completed.single { it.status == "COMPLETED" }
        assertEquals(at(10).toEpochMilli(), old.dueAt)
        assertEquals(completed, MedicationReminders.reconcile(completed, rows, listOf(fact.copy(dose = "1")), at(9).toEpochMilli()))
        val reopened = MedicationReminders.reconcile(completed, rows, emptyList(), at(9).toEpochMilli())
        assertEquals("WAITING", reopened.single { it.key == old.key }.status)
        val expired = MedicationReminders.reconcile(completed, rows, emptyList(), at(14).toEpochMilli())
        assertEquals("EXPIRED", expired.single { it.key == old.key }.status)
        assertNull(expired.single { it.key == old.key }.nextAt)
    }
    @Test fun completedFoodTaskDoesNotReturnWhenMealIsRegisteredOrMovedLater() {
        val data = MedicationData(listOf(drug), listOf(regimen), listOf(version.copy(food = "AFTER",
            foodOffset = true, meal = "LUNCH", offsetMinutes = 30)))
        val rows = desired(data, at(14))
        val row = rows.first { it.dueAt == at(13).plusSeconds(1800).toEpochMilli() }
        val fact = MedicationIntake("i", "m", "r", "v", "0.5", "табл.", at(14).toEpochMilli(), 1, 1)
        val completed = row.copy(status = "COMPLETED", nextAt = null, recordId = fact.id,
            completionSignature = MedicationReminders.signature(fact))
        val moved = desired(data, at(14), listOf(MealEvent("meal", "LUNCH", at(16).toEpochMilli(), 1, 1)))
        val merged = ReminderSchedule.merge(listOf(completed), moved, at(14).toEpochMilli())
        val matched = MedicationReminders.reconcile(merged, moved, listOf(fact), at(14).toEpochMilli())
        assertEquals(completed, matched.single { it.key == row.key })
        val removed = MedicationReminders.reconcile(matched, moved, emptyList(), at(14).toEpochMilli())
        assertEquals(at(16).plusSeconds(1800).toEpochMilli(), removed.single { it.key == row.key }.nextAt)
    }
    @Test fun sparseAndFarFutureAssignmentsStillRegisterTheirNextOccurrence() {
        val sparse = version.copy(mode = "DAYS", everyDays = 3650, minutes = "480")
        val data = MedicationData(listOf(drug), listOf(regimen), listOf(sparse))
        val rows = desired(data)
        val expected = date.plusDays(3650).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, rows.filter { it.dueAt >= at(9).toEpochMilli() }.single().dueAt)
        val start = date.plusYears(3).atStartOfDay(zone).toInstant().toEpochMilli()
        val future = desired(MedicationData(listOf(drug), listOf(regimen.copy(startsAt = start)),
            listOf(version.copy(effectiveAt = start))))
        assertTrue(future.any { it.dueAt >= start })
    }
}
