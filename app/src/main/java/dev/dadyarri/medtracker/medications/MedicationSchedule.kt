package dev.dadyarri.medtracker.medications

import dev.dadyarri.medtracker.meals.MealEvent
import dev.dadyarri.medtracker.reminders.ReminderKind
import dev.dadyarri.medtracker.reminders.ReminderSetting
import java.time.*
import java.time.temporal.ChronoUnit

data class MedicationTask(val key: String, val regimenId: String, val versionId: String,
                          val at: Long, val dose: String, val unit: String)

/** Pure calendar calculation. No synthetic intake is ever used as an interval anchor. */
object MedicationSchedule {
    /** Find the next sparse/future assignment without materializing years of daily occurrences. */
    fun next(regimen: MedicationRegimen, versions: List<RegimenVersion>, intakes: List<MedicationIntake>,
             meals: List<MealEvent>, settings: List<ReminderSetting>, now: Instant, zone: ZoneId): MedicationTask? {
        val history = versions.filter { it.regimenId == regimen.id }
        return history.flatMap { version ->
            val lower = maxOf(now.toEpochMilli(), regimen.startsAt, version.effectiveAt)
            val lowerDate = Instant.ofEpochMilli(lower).atZone(zone).toLocalDate()
            val dates = when (ScheduleMode.valueOf(version.mode)) {
                ScheduleMode.PRN -> emptyList()
                ScheduleMode.ONCE -> listOfNotNull(version.onceAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() })
                ScheduleMode.HOURS -> listOfNotNull(intakes.filter { it.regimenId == regimen.id && it.takenAt <= now.toEpochMilli() }
                    .maxOfOrNull { it.takenAt }?.let { Instant.ofEpochMilli(it + version.everyHours * 3_600_000L).atZone(zone).toLocalDate() })
                ScheduleMode.DAYS -> {
                    val anchor = LocalDate.parse(version.anchorDate)
                    val base = maxOf(lowerDate.minusDays(1), anchor)
                    val elapsed = ChronoUnit.DAYS.between(anchor, base)
                    val date = base.plusDays(Math.floorMod(-elapsed, version.everyDays.toLong()))
                    (0L..2L).map { date.plusDays(it * version.everyDays) }
                }
                else -> listOf(lowerDate)
            }
            dates.flatMap { date ->
                val from = maxOf(now, date.minusDays(1).atStartOfDay(zone).toInstant())
                val until = date.plusDays(9).atStartOfDay(zone).toInstant()
                if (until <= from) emptyList() else tasks(regimen, versions, intakes, meals, settings, from, until, zone, asOf = now)
                    .filter { it.versionId == version.id }
            }
        }.minWithOrNull(compareBy<MedicationTask> { it.at }.thenBy { it.key })
    }
    fun tasks(regimen: MedicationRegimen, versions: List<RegimenVersion>, intakes: List<MedicationIntake>,
              meals: List<MealEvent>, settings: List<ReminderSetting>, from: Instant, until: Instant,
              zone: ZoneId, asOf: Instant = from): List<MedicationTask> {
        require(until > from && Duration.between(from, until).toDays() <= 370)
        val history = versions.filter { it.regimenId == regimen.id }.sortedBy { it.effectiveAt }
        val output = mutableListOf<MedicationTask>()
        fun add(at: Long, version: RegimenVersion, key: String) {
            if (at >= from.toEpochMilli() && at < until.toEpochMilli() && regimen.activeAt(at) &&
                history.at(regimen.id, at)?.id == version.id)
                output += MedicationTask("${regimen.id}/$key", regimen.id, version.id, at, version.dose, version.unit)
        }
        history.forEach { version ->
            when (ScheduleMode.valueOf(version.mode)) {
                ScheduleMode.PRN -> Unit
                ScheduleMode.HOURS -> {
                    val last = intakes.filter { it.regimenId == regimen.id && it.takenAt <= asOf.toEpochMilli() }
                        .maxWithOrNull(compareBy<MedicationIntake> { it.takenAt }.thenBy { it.id })
                    if (last != null) add(Math.addExact(last.takenAt, version.everyHours * 3_600_000L), version, "interval/${last.id}")
                }
                ScheduleMode.ONCE -> version.onceAt?.let { add(it, version, "once") }
                else -> {
                    var date = from.atZone(zone).toLocalDate().minusDays(1)
                    val last = until.atZone(zone).toLocalDate().plusDays(1)
                    while (!date.isAfter(last)) {
                        val days = ChronoUnit.DAYS.between(LocalDate.parse(version.anchorDate), date)
                        val eligible = when (ScheduleMode.valueOf(version.mode)) {
                            ScheduleMode.WEEKDAYS -> date.dayOfWeek.value in version.days()
                            ScheduleMode.DAYS -> days >= 0 && days % version.everyDays == 0L
                            else -> true
                        }
                        if (eligible) {
                            if (version.foodOffset) {
                                val kind = ReminderKind.valueOf(version.meal)
                                val minute = settings.find { it.kind == kind }?.minute ?: kind.defaultMinute
                                val expected = date.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
                                val first = meals.filter { it.type == version.meal &&
                                    Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == date }.minOfOrNull { it.startedAt }
                                val base = if (version.food == "AFTER") first ?: expected else expected
                                val offset = version.offsetMinutes * 60_000L * if (version.food == "BEFORE") -1 else 1
                                add(base + offset, version, "$date/food")
                            } else version.times().forEach { minute ->
                                add(date.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli(), version, "$date/$minute")
                            }
                        }
                        date = date.plusDays(1)
                    }
                }
            }
        }
        return output.distinctBy { it.key }.sortedWith(compareBy<MedicationTask> { it.at }.thenBy { it.key })
    }
}
