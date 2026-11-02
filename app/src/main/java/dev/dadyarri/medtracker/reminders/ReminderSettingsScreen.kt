package dev.dadyarri.medtracker.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReminderSettingsScreen(
    model: ReminderSettingsViewModel = koinViewModel(),
    extra: @Composable () -> Unit = {},
) {
    val values by model.settings.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        error?.let { item("error") { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (values.isEmpty() && error == null) item("loading") { CircularProgressIndicator() }
        item("measurements_title") { Text("Измерения", style = MaterialTheme.typography.titleMedium) }
        items(values.filter { it.kind in listOf(ReminderKind.PRESSURE, ReminderKind.WEIGHT, ReminderKind.GLUCOSE) },
            key = { it.kind }) { ReminderCard(it, busy, model::save) }
        item("meals_title") { Text("Питание", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
        items(values.filter { it.kind !in listOf(ReminderKind.PRESSURE, ReminderKind.WEIGHT, ReminderKind.GLUCOSE) },
            key = { it.kind }) { ReminderCard(it, busy, model::save) }
        item("extra") { extra() }
    }
}
@Composable
internal fun ReminderCard(value: ReminderSetting, busy: Boolean, onSave: (ReminderSetting) -> Unit) {
    var picker by rememberSaveable(value.kind) { mutableStateOf<String?>(null) }
    var frequency by rememberSaveable(value.kind) { mutableStateOf(false) }
    var weeks by rememberSaveable(value.kind, value.weeks) { mutableStateOf(value.weeks.toString()) }
    val context = LocalContext.current
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(value.kind.icon), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(value.kind.label, style = MaterialTheme.typography.titleMedium)
                    Text(if (value.paused) "Приостановлено" else if (value.kind == ReminderKind.WEIGHT)
                        "Раз в ${value.weeks} нед." else "Каждый день",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = !value.paused, enabled = !busy, onCheckedChange = { onSave(value.copy(paused = !it)) },
                    modifier = Modifier.semantics { contentDescription = "Напоминания: ${value.kind.label}" })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { picker = "time" }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "Время: ${value.kind.label}" }) {
                    Icon(painterResource(dev.dadyarri.medtracker.R.drawable.ic_clock), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("%02d:%02d".format(value.minute / 60, value.minute % 60))
                }
                if (value.kind == ReminderKind.WEIGHT) {
                    OutlinedButton(onClick = { frequency = true }, enabled = !busy) { Text("Раз в ${value.weeks} нед.") }
                    OutlinedButton(onClick = { picker = "date" }, enabled = !busy) {
                        Text(value.anchor.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")))
                    }
                }
            }
        }
    }
    if (frequency) AlertDialog(onDismissRequest = { frequency = false }, title = { Text("Частота взвешивания") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicTextField(value = weeks, onValueChange = { weeks = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Количество недель" })
                Text("недель", style = MaterialTheme.typography.labelMedium)
                if (weeks.toIntOrNull() !in 1..52) Text("Укажите от 1 до 52 недель", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(enabled = !busy && weeks.toIntOrNull() in 1..52, onClick = {
            onSave(value.copy(weeks = weeks.toInt())); frequency = false
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { frequency = false }) { Text("Отмена") } })
    if (picker != null) DisposableEffect(picker) {
        val dialog = if (picker == "time") TimePickerDialog(context, { _, h, m ->
            onSave(value.copy(minute = h * 60 + m))
        }, value.minute / 60, value.minute % 60, android.text.format.DateFormat.is24HourFormat(context))
        else DatePickerDialog(context, { _, y, m, d -> onSave(value.copy(anchor = LocalDate.of(y, m + 1, d))) },
            value.anchor.year, value.anchor.monthValue - 1, value.anchor.dayOfMonth)
        dialog.setOnDismissListener { picker = null }; dialog.show()
        onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
    }
}
