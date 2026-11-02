package dev.dadyarri.medtracker.meals

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.measurements.MealType
import dev.dadyarri.medtracker.pressure.PressureDraft
import java.time.*

@Composable
fun MealEditor(initial: MealEvent, busy: Boolean, onSave: (MealEvent) -> Unit, onDelete: () -> Unit) {
    var time by rememberSaveable(initial.id) { mutableStateOf(initial.startedAt) }
    var type by rememberSaveable(initial.id) { mutableStateOf(initial.type) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val selected = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault())
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Начало еды", style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealType.entries.forEach { item ->
                FilterChip(selected = type == item.name, enabled = !busy, onClick = { type = item.name }, label = { Text(item.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker = "date" }, enabled = !busy) { Text(PressureDraft.format(time).substringBefore(' ')) }
            OutlinedButton(onClick = { picker = "time" }, enabled = !busy) { Text(PressureDraft.format(time).substringAfter(' ')) }
        }
        Button(onClick = { onSave(initial.copy(type = type, startedAt = time)) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        TextButton(onClick = { deleting = true }, enabled = !busy) { Text("Удалить запись") }
    }
    if (picker != null) DisposableEffect(picker) {
        val dialog = if (picker == "date") DatePickerDialog(context, { _, y, m, d ->
            time = LocalDate.of(y, m + 1, d).atTime(selected.toLocalTime()).atZone(selected.zone).toInstant().toEpochMilli()
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth)
        else TimePickerDialog(context, { _, h, m ->
            time = selected.withHour(h).withMinute(m).toInstant().toEpochMilli()
        }, selected.hour, selected.minute, android.text.format.DateFormat.is24HourFormat(context))
        dialog.setOnDismissListener { picker = null }; dialog.show()
        onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Удалить запись?") },
        text = { Text("Измерения глюкозы сохранятся.") },
        confirmButton = { TextButton(onClick = onDelete, enabled = !busy) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } })
}
