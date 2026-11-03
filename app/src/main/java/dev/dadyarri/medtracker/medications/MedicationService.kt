package dev.dadyarri.medtracker.medications

import androidx.room.withTransaction
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.measurements.MealType
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class MedicationService(private val db: AlarmDatabase) {
    private val dao get() = db.medications()
    suspend fun save(medication: Medication) = db.withTransaction {
        require(medication.name.isNotBlank() && medication.unit.isNotBlank())
        val dose = doseValue(medication.usualDose) ?: error("Invalid dose")
        val shape = PillShape.valueOf(medication.shape)
        require(medication.score in 0..2)
        require(medication.score == 0 || shape.supportsScore)
        val old = dao.medication(medication.id)
        if (old != null && old.unit != medication.unit.trim()) {
            require(dao.intakes().first().none { it.medicationId == old.id } &&
                dao.regimens().first().none { it.medicationId == old.id }) { "Unit has history" }
        }
        val now = System.currentTimeMillis()
        val value = medication.copy(name = medication.name.trim(), unit = medication.unit.trim(), usualDose = dose,
            createdAt = old?.createdAt ?: now, updatedAt = now)
        if (old == null) dao.insert(value) else check(dao.update(value) == 1)
    }
    suspend fun archive(id: String, archived: Boolean) = db.withTransaction {
        val old = dao.medication(id) ?: error("Missing medication")
        if (archived) {
            val now = System.currentTimeMillis()
            dao.regimens().first().filter { it.medicationId == id && (it.completedAt == null || it.completedAt > now) && (it.endsAt == null || it.endsAt > now) }.forEach {
                dao.update(it.copy(completedAt = now))
            }
        }
        check(dao.update(old.copy(archived = archived, updatedAt = System.currentTimeMillis())) == 1)
    }
    suspend fun prescribe(regimen: MedicationRegimen, version: RegimenVersion) = db.withTransaction {
        val medication = dao.medication(regimen.medicationId) ?: error("Missing medication")
        require(!medication.archived && version.regimenId == regimen.id && version.effectiveAt == regimen.startsAt)
        val kind = RegimenKind.valueOf(regimen.kind)
        require(regimen.endsAt == null || regimen.endsAt > regimen.startsAt)
        require(kind != RegimenKind.COURSE || regimen.endsAt != null)
        require((kind == RegimenKind.PRN) == (version.mode == ScheduleMode.PRN.name))
        require((kind == RegimenKind.ONCE) == (version.mode == ScheduleMode.ONCE.name))
        validate(version, medication.unit)
        dao.insert(regimen)
        dao.insert(version.copy(dose = doseValue(version.dose)!!))
    }
    suspend fun revise(version: RegimenVersion) = db.withTransaction {
        val regimen = dao.regimen(version.regimenId) ?: error("Missing regimen")
        require(regimen.completedAt == null && version.effectiveAt > regimen.startsAt)
        require(regimen.endsAt == null || version.effectiveAt < regimen.endsAt)
        val current = dao.versions().first().at(regimen.id, version.effectiveAt) ?: error("Missing version")
        require(version.effectiveAt > current.effectiveAt)
        require(dao.versions().first().none { it.regimenId == regimen.id && it.effectiveAt == version.effectiveAt })
        require((regimen.kind == "PRN") == (version.mode == "PRN") && (regimen.kind == "ONCE") == (version.mode == "ONCE"))
        validate(version, dao.medication(regimen.medicationId)!!.unit)
        dao.insert(version.copy(dose = doseValue(version.dose)!!))
    }
    suspend fun finish(id: String, at: Long = System.currentTimeMillis()) = db.withTransaction {
        val regimen = dao.regimen(id) ?: error("Missing regimen")
        require(at > 0)
        check(dao.update(regimen.copy(completedAt = minOf(regimen.completedAt ?: at, at))) == 1)
    }
    suspend fun saveIntake(value: MedicationIntake, editing: Boolean) = db.withTransaction {
        val medication = dao.medication(value.medicationId) ?: error("Missing medication")
        val dose = doseValue(value.dose) ?: error("Invalid dose")
        require(value.unit == medication.unit)
        val old = if (editing) dao.intake(value.id) ?: error("Missing intake") else null
        require(old == null || old.medicationId == value.medicationId)
        val regimen = value.regimenId?.let { dao.regimen(it) ?: error("Missing regimen") }
        require(regimen == null || regimen.medicationId == medication.id)
        // Facts may be registered late, including after a course was completed.
        val version = regimen?.let { dao.versions().first().at(it.id, value.takenAt) ?: error("Before regimen start") }
        val now = System.currentTimeMillis()
        val intake = value.copy(dose = dose, versionId = version?.id, createdAt = old?.createdAt ?: now, updatedAt = now)
        if (old == null) dao.insert(intake) else check(dao.update(intake) == 1)
    }
    suspend fun deleteIntake(id: String) { check(dao.deleteIntake(id) == 1) }
    private fun validate(version: RegimenVersion, unit: String) {
        require(doseValue(version.dose) != null && version.unit == unit)
        val mode = ScheduleMode.valueOf(version.mode)
        LocalDate.parse(version.anchorDate)
        require(version.times().isNotEmpty() && version.times().all { it in 0..1439 })
        require(version.minutes.split(',').all { it.toIntOrNull() in 0..1439 })
        require(version.days().isNotEmpty() && version.days().all { it in 1..7 })
        require(version.everyDays in 1..3650 && version.everyHours in 1..8760)
        require(mode != ScheduleMode.ONCE || version.onceAt != null)
        val food = MedicationFood.valueOf(version.food)
        require(version.offsetMinutes in 0..1440)
        require(MealType.valueOf(version.meal) != MealType.SNACK)
        require(!version.foodOffset || food in listOf(MedicationFood.BEFORE, MedicationFood.AFTER))
        require(!version.foodOffset || mode !in listOf(ScheduleMode.HOURS, ScheduleMode.ONCE, ScheduleMode.PRN))
    }
}
