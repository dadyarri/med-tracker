package dev.dadyarri.medtracker.reminders

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import java.time.format.DateTimeFormatter

@Composable
fun ReminderSettingsScreen(
    model: ReminderSettingsViewModel = koinViewModel(),
    extra: @Composable (List<ReminderSetting>) -> Unit = {},
) {
    val values by model.settings.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val schedulingMessage by model.schedulingMessage.collectAsStateWithLifecycle()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        error?.let { item("error") { Text(it, color = MaterialTheme.colorScheme.error) } }
        schedulingMessage?.let { item("scheduling_error") { Text(it, color = MaterialTheme.colorScheme.error) } }
        item("general_title") { Text("Общее", style = MaterialTheme.typography.titleMedium) }
        item("permission_status") { ReminderPermissionStatus() }
        item("alarm_sound") { AlarmSoundSetting() }
        if (values.isEmpty() && error == null) item("loading") { CircularProgressIndicator() }
        item("measurements_title") { Text("Измерения", style = MaterialTheme.typography.titleMedium) }
        items(values.filter { it.kind in listOf(ReminderKind.PRESSURE, ReminderKind.WEIGHT) },
            key = { it.kind }) { ReminderCard(it, busy, model::save) }
        item("glucose_cycle") { extra(values) }
        item("meals_title") { Text("Питание", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
        items(values.filter { it.kind !in listOf(ReminderKind.PRESSURE, ReminderKind.WEIGHT) },
            key = { it.kind }) { ReminderCard(it, busy, model::save) }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderCard(value: ReminderSetting, busy: Boolean, onSave: (ReminderSetting) -> Unit) {
    var picker by rememberSaveable(value.kind) { mutableStateOf<String?>(null) }
    var frequency by rememberSaveable(value.kind) { mutableStateOf(false) }
    var weeks by rememberSaveable(value.kind, value.weeks) { mutableStateOf(value.weeks.toString()) }
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
    if (picker == "date") dev.dadyarri.medtracker.ui.AppDatePicker(value.anchor, { picker = null }) {
        onSave(value.copy(anchor = it)); picker = null
    }
    if (picker == "time") dev.dadyarri.medtracker.ui.AppTimePicker(value.minute / 60, value.minute % 60, { picker = null }) { h, m ->
        onSave(value.copy(minute = h * 60 + m)); picker = null
    }
}
