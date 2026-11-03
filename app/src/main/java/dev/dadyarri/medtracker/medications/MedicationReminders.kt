package dev.dadyarri.medtracker.medications

import dev.dadyarri.medtracker.meals.MealEvent
import dev.dadyarri.medtracker.reminders.ReminderOccurrence
import dev.dadyarri.medtracker.reminders.ReminderSetting
import java.time.*
import kotlin.math.abs

/** Medication tasks share the native alarm pipeline; persisted payloads identify exact assignments. */
internal object MedicationReminders {
    fun desired(data: MedicationData, meals: List<MealEvent>, settings: List<ReminderSetting>,
                now: Instant, zone: ZoneId): List<ReminderOccurrence> {
        val today = now.atZone(zone).toLocalDate()
        val drugs = data.medications.filterNot { it.archived }.associateBy { it.id }
        return data.regimens.flatMap { regimen ->
            if (regimen.completedAt?.let { it <= now.toEpochMilli() } == true ||
                regimen.endsAt?.let { it <= now.toEpochMilli() } == true) return@flatMap emptyList()
            val drug = drugs[regimen.medicationId] ?: return@flatMap emptyList()
            val upcoming = MedicationSchedule.next(regimen, data.versions, data.intakes, meals, settings, now, zone)
            (MedicationSchedule.tasks(regimen, data.versions, data.intakes, meals, settings,
                today.minusDays(1).atStartOfDay(zone).toInstant(), today.plusDays(9).atStartOfDay(zone).toInstant(),
                zone, asOf = now) + listOfNotNull(upcoming)).distinctBy { it.key }.map { task ->
                ReminderOccurrence("MEDICATION/${task.key}", "MEDICATION", "${task.versionId}/${task.at}/${task.dose}/${task.unit}",
                    "${drug.name} · ${task.dose.replace('.', ',')} ${task.unit}", task.at, task.at, task.at + 120 * 60_000L,
                    medicationId = drug.id, regimenId = task.regimenId, versionId = task.versionId,
                    dose = task.dose, unit = task.unit)
            }
        }
    }

    fun signature(record: MedicationIntake) = "${record.takenAt}/${record.regimenId}/${record.versionId}"

    /** Keep completed food tasks completed when a late/corrected meal moves the planned time. */
    fun reconcile(rows: List<ReminderOccurrence>, desired: List<ReminderOccurrence>,
                  intakes: List<MedicationIntake>, now: Long): List<ReminderOccurrence> {
        val wanted = desired.filter { it.source == "MEDICATION" }.associateBy { it.key }
        val facts = intakes.associateBy { it.id }
        val used = mutableSetOf<String>()
        val result = rows.associateBy { it.key }.toMutableMap()
        fun matches(row: ReminderOccurrence, record: MedicationIntake) =
            record.medicationId == row.medicationId && record.regimenId != null &&
                record.regimenId == row.regimenId && record.versionId == row.versionId &&
                record.takenAt in (row.dueAt - 30 * 60_000L)..row.endAt
        rows.filter { it.source == "MEDICATION" && it.status == "COMPLETED" }.sortedBy { it.key }.forEach { row ->
            val fact = facts[row.recordId]
            val explicit = fact != null && row.completionSignature == signature(fact) &&
                fact.medicationId == row.medicationId && fact.regimenId == row.regimenId && fact.versionId == row.versionId
            result[row.key] = if (fact != null && (explicit || matches(row, fact)) && used.add(fact.id)) row
            else {
                val candidate = wanted[row.key]
                if (candidate != null && candidate.dueAt > now) candidate
                else (candidate ?: row).copy(status = if (candidate == null) "CANCELLED" else "EXPIRED",
                    nextAt = null, token = "", recordId = null, completionSignature = null)
            }
        }
        intakes.sortedWith(compareBy<MedicationIntake> { it.takenAt }.thenBy { it.createdAt }.thenBy { it.id })
            .filterNot { it.id in used }.forEach { fact ->
                val row = result.values.filter {
                    it.source == "MEDICATION" && it.key in wanted && it.canRecord && matches(it, fact)
                }.minWithOrNull(compareBy<ReminderOccurrence> { abs(it.dueAt - fact.takenAt) }.thenBy { it.key })
                if (row != null) {
                    used.add(fact.id)
                    result[row.key] = row.copy(status = "COMPLETED", nextAt = null, token = "", recordId = fact.id,
                        completionSignature = signature(fact))
                }
            }
        return rows.map { result.getValue(it.key) }
    }
}
