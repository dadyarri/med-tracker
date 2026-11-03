package dev.dadyarri.medtracker.meals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.measurements.MealType
import dev.dadyarri.medtracker.pressure.PressureDraft
import java.time.*

@Composable
fun MealEditor(initial: MealEvent, busy: Boolean, onSave: (MealEvent) -> Unit, onDelete: () -> Unit,
    editing: Boolean = true, onClose: (() -> Unit)? = null, error: String? = null) {
    var time by rememberSaveable(initial.id) { mutableStateOf(initial.startedAt) }
    var type by rememberSaveable(initial.id) { mutableStateOf(initial.type) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Начало еды", style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { item ->
                FilterChip(selected = type == item.name, enabled = !busy, onClick = { type = item.name }, label = { Text(item.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker = "date" }, enabled = !busy, modifier = Modifier.weight(1.3f)) {
                Icon(androidx.compose.ui.res.painterResource(dev.dadyarri.medtracker.R.drawable.ic_calendar), "Дата начала еды", Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(PressureDraft.format(time).substringBefore(' '))
            }
            OutlinedButton(onClick = { picker = "time" }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(androidx.compose.ui.res.painterResource(dev.dadyarri.medtracker.R.drawable.ic_clock), "Время начала еды", Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp)); Text(PressureDraft.format(time).substringAfter(' '))
            }
        }
        Button(onClick = { onSave(initial.copy(type = type, startedAt = time)) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (editing) TextButton(onClick = { deleting = true }, enabled = !busy) { Text("Удалить запись") }
        onClose?.let { close -> TextButton(onClick = close, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") } }
    }
    if (picker == "date") dev.dadyarri.medtracker.ui.AppDatePicker(selected.toLocalDate(), { picker = null }) { day ->
        time = day.atTime(selected.toLocalTime()).atZone(selected.zone).toInstant().toEpochMilli(); picker = null
    }
    if (picker == "time") dev.dadyarri.medtracker.ui.AppTimePicker(selected.hour, selected.minute, { picker = null }) { h, m ->
        time = selected.withHour(h).withMinute(m).toInstant().toEpochMilli(); picker = null
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Удалить запись?") },
        text = { Text("Измерения глюкозы сохранятся.") },
        confirmButton = { TextButton(onClick = onDelete, enabled = !busy) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } })
}
