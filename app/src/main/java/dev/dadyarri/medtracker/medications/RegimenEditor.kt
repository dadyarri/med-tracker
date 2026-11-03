package dev.dadyarri.medtracker.medications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.R
import dev.dadyarri.medtracker.measurements.MealType
import dev.dadyarri.medtracker.measurements.icon
import dev.dadyarri.medtracker.ui.AppTimePicker
import java.time.*

@Composable
internal fun RegimenEditor(medication: Medication, existing: MedicationRegimen?, initial: RegimenVersion?,
                           busy: Boolean, error: String?, repeatFrom: MedicationRegimen? = null, onSave: (MedicationRegimen, RegimenVersion) -> Unit,
                           onClose: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val newId = rememberSaveable { medicationId() }
    val versionId = rememberSaveable { medicationId() }
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: repeatFrom?.kind ?: "CONTINUOUS") }
    var start by rememberSaveable { mutableLongStateOf(if (existing == null) today.atStartOfDay(zone).toInstant().toEpochMilli() else maxOf(System.currentTimeMillis(), existing.startsAt + 1)) }
    val length = repeatFrom?.endsAt?.let { java.time.temporal.ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(repeatFrom.startsAt).atZone(zone).toLocalDate(), Instant.ofEpochMilli(it).atZone(zone).toLocalDate()) } ?: 7L
    var end by rememberSaveable { mutableLongStateOf(today.plusDays(maxOf(1L, length) - 1).atStartOfDay(zone).toInstant().toEpochMilli()) }
    var once by rememberSaveable { mutableLongStateOf(initial?.onceAt?.takeIf { existing != null } ?: System.currentTimeMillis()) }
    var dose by rememberSaveable { mutableStateOf(initial?.dose ?: medication.usualDose) }
    var selectedMode by rememberSaveable { mutableStateOf(initial?.mode ?: "DAILY") }
    var minutes by rememberSaveable { mutableStateOf(initial?.minutes ?: "480") }
    var weekdays by rememberSaveable { mutableStateOf(initial?.weekdays ?: "1,2,3,4,5,6,7") }
    var everyDays by rememberSaveable { mutableStateOf((initial?.everyDays ?: 1).toString()) }
    var everyHours by rememberSaveable { mutableStateOf((initial?.everyHours ?: 8).toString()) }
    var food by rememberSaveable { mutableStateOf(initial?.food ?: "NONE") }
    var offset by rememberSaveable { mutableStateOf(initial?.foodOffset ?: false) }
    var meal by rememberSaveable { mutableStateOf(initial?.meal ?: "BREAKFAST") }
    var offsetMinutes by rememberSaveable { mutableStateOf((initial?.offsetMinutes ?: 30).toString()) }
    var timeTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    val modes = listOf(ScheduleMode.DAILY, ScheduleMode.WEEKDAYS, ScheduleMode.DAYS, ScheduleMode.HOURS)
    val mode = when (kind) {
        "PRN" -> ScheduleMode.PRN
        "ONCE" -> ScheduleMode.ONCE
        else -> modes.find { it.name == selectedMode } ?: ScheduleMode.DAILY
    }
    val times = minutes.split(',').mapNotNull(String::toIntOrNull).distinct().sorted()
    val effectiveOffset = offset && food in listOf("BEFORE", "AFTER") && mode in listOf(ScheduleMode.DAILY, ScheduleMode.WEEKDAYS, ScheduleMode.DAYS)
    val startDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
    val endExclusive = Instant.ofEpochMilli(end).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val regimen = existing ?: MedicationRegimen(newId, medication.id, kind, start,
        if (kind == "COURSE") endExclusive else null, createdAt = System.currentTimeMillis())
    val version = RegimenVersion(versionId, regimen.id, start, dose, medication.unit, mode.name,
        minutes, weekdays, everyDays.toIntOrNull() ?: 0, everyHours.toIntOrNull() ?: 0,
        if (existing != null) initial?.anchorDate ?: startDate.toString() else startDate.toString(),
        once, food, effectiveOffset, meal, offsetMinutes.toIntOrNull() ?: -1)
    val valid = doseValue(dose) != null && everyDays.toIntOrNull() in 1..3650 && everyHours.toIntOrNull() in 1..8760 &&
        times.isNotEmpty() && weekdays.isNotBlank() && offsetMinutes.toIntOrNull() in 0..1440 &&
        (kind != "COURSE" || endExclusive > start) &&
        (mode != ScheduleMode.ONCE || once >= start)
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (existing == null) "Назначение" else "Новая версия схемы", style = MaterialTheme.typography.headlineSmall)
        Text(medication.name, style = MaterialTheme.typography.titleMedium)
        if (existing == null) MedicationChoice(RegimenKind.valueOf(kind), RegimenKind.entries, { it.label }, !busy) { kind = it.name }
        MedicationNumber("Доза · ${medication.unit}", dose, !busy) { dose = it }
        Text(if (existing == null) "Начало" else "Вступает в силу", style = MaterialTheme.typography.labelMedium)
        MedicationDateTime(start, !busy, "Начало назначения", includeTime = existing != null) { start = it }
        if (kind == "COURSE" && existing == null) {
            Text("Последний день", style = MaterialTheme.typography.labelMedium)
            MedicationDateTime(end, !busy, "Конец курса", includeTime = false) { end = it }
        }
        if (mode !in listOf(ScheduleMode.PRN, ScheduleMode.ONCE)) {
            MedicationChoice(mode, modes, { it.label }, !busy) { selectedMode = it.name }
        }
        if (mode == ScheduleMode.DAYS) MedicationNumber("Дней", everyDays, !busy) { everyDays = it }
        if (mode == ScheduleMode.HOURS) MedicationNumber("Часов после приёма", everyHours, !busy) { everyHours = it }
        if (mode == ScheduleMode.WEEKDAYS) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val selected = day.value in weekdays.split(',').mapNotNull(String::toIntOrNull)
                    FilterChip(selected, onClick = {
                        val days = weekdays.split(',').mapNotNull(String::toIntOrNull).toMutableSet()
                        if (selected) days.remove(day.value) else days.add(day.value)
                        weekdays = days.sorted().joinToString(",")
                    }, enabled = !busy, label = { Text(listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")[day.value - 1]) })
                }
            }
        }
        if (mode == ScheduleMode.ONCE) MedicationDateTime(once, !busy, "Разовый приём") { once = it }
        if (mode in listOf(ScheduleMode.DAILY, ScheduleMode.WEEKDAYS, ScheduleMode.DAYS) && !effectiveOffset) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                times.forEach { minute ->
                    InputChip(selected = false, onClick = { timeTarget = minute }, enabled = !busy,
                        label = { Text("%02d:%02d".format(minute / 60, minute % 60)) },
                        trailingIcon = { if (times.size > 1) IconButton(onClick = {
                            minutes = times.filterNot { it == minute }.joinToString(",")
                        }, enabled = !busy, modifier = Modifier.size(24.dp)) {
                            Icon(painterResource(R.drawable.ic_context_unlinked), "Удалить время", Modifier.size(18.dp))
                        } })
                }
                IconButton(onClick = { timeTarget = -1 }, enabled = !busy) {
                    Text("+", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MedicationFood.entries.forEach { relation ->
                val icon = when (relation) {
                    MedicationFood.NONE -> R.drawable.ic_context_unlinked
                    MedicationFood.BEFORE -> R.drawable.ic_context_before_meal
                    MedicationFood.DURING -> R.drawable.ic_meal
                    MedicationFood.AFTER -> R.drawable.ic_context_after_meal
                }
                FilterChip(selected = food == relation.name, onClick = { food = relation.name }, enabled = !busy,
                    label = { Icon(painterResource(icon), relation.label, Modifier.size(24.dp)) })
            }
        }
        if (food in listOf("BEFORE", "AFTER") && mode in listOf(ScheduleMode.DAILY, ScheduleMode.WEEKDAYS, ScheduleMode.DAYS)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Смещение от еды", Modifier.weight(1f))
                Switch(offset, { offset = it }, enabled = !busy)
            }
            if (effectiveOffset) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    MealType.entries.filterNot { it == MealType.SNACK }.forEach { type ->
                        FilterChip(selected = meal == type.name, onClick = { meal = type.name }, enabled = !busy,
                            label = { Icon(painterResource(type.icon), type.label, Modifier.size(24.dp)) })
                    }
                }
                MedicationNumber("Минут", offsetMinutes, !busy) { offsetMinutes = it }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(regimen, version) }, enabled = !busy && valid, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        TextButton(onClick = onClose, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
    }
    timeTarget?.let { target ->
        val value = target.takeIf { it >= 0 } ?: 480
        AppTimePicker(value / 60, value % 60, { timeTarget = null }) { h, m ->
            minutes = (times.filterNot { it == target } + (h * 60 + m)).distinct().sorted().joinToString(",")
            timeTarget = null
        }
    }
}
