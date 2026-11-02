package dev.dadyarri.medtracker.pressure

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.dadyarri.medtracker.R
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
internal fun PressureEditor(
    id: String, initial: List<String>, editing: Boolean, busy: Boolean, error: String?,
    onSave: (PressureDraft) -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
) {
    var systolic by rememberSaveable(id) { mutableStateOf(initial[0]) }
    var diastolic by rememberSaveable(id) { mutableStateOf(initial[1]) }
    var pulse by rememberSaveable(id) { mutableStateOf(initial[2]) }
    var time by rememberSaveable(id) { mutableStateOf(initial[3]) }
    var manualContext by rememberSaveable(id) { mutableStateOf(initial[4].ifEmpty { null }) }
    var automaticContext by rememberSaveable(id) { mutableStateOf(!editing) }
    var picker by rememberSaveable(id) { mutableStateOf<String?>(null) }
    val localTime = runCatching { LocalDateTime.parse(time, PressureDraft.TIME_FORMAT) }.getOrElse { LocalDateTime.now() }
    val context = if (automaticContext) PressureContext.forHour(localTime.hour).name else manualContext
    val keyboard = LocalSoftwareKeyboardController.current
    // Keep historical comments when editing; the compact form no longer exposes this field.
    val comment = initial[5]
    var deleting by rememberSaveable(id) { mutableStateOf(false) }
    var confirmUnusual by rememberSaveable(id) { mutableStateOf(false) }
    var attempted by rememberSaveable(id) { mutableStateOf(false) }
    val diastolicFocus = remember { FocusRequester() }
    val pulseFocus = remember { FocusRequester() }
    val timeFocus = remember { FocusRequester() }
    val draft = PressureDraft(systolic, diastolic, pulse, time, context, comment, initial.getOrNull(6)?.toLongOrNull())
    BackHandler(enabled = busy) { }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (editing) "Изменить давление" else "Записать давление", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            NumberInput("Верхнее", systolic, busy, Modifier.weight(1f)) { value ->
                val advance = completedNumber(systolic, value, '7'..'9', 70..250)
                systolic = value
                if (advance) diastolicFocus.requestFocus()
            }
            NumberInput("Нижнее", diastolic, busy, Modifier.weight(1f).focusRequester(diastolicFocus)) { value ->
                val advance = completedNumber(diastolic, value, '4'..'9', 40..150)
                diastolic = value
                if (advance) pulseFocus.requestFocus()
            }
        }
        NumberInput("Пульс", pulse, busy, Modifier.focusRequester(pulseFocus)) { value ->
            val advance = completedNumber(pulse, value, '3'..'9', 30..220)
            pulse = value
            if (advance) {
                timeFocus.requestFocus()
                keyboard?.hide()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { keyboard?.hide(); picker = "date" }, enabled = !busy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1.3f).semantics { contentDescription = "Дата измерения" }) {
                Icon(painterResource(dev.dadyarri.medtracker.R.drawable.ic_calendar), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(localTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")))
            }
            OutlinedButton(onClick = { keyboard?.hide(); picker = "time" }, enabled = !busy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).focusRequester(timeFocus).semantics { contentDescription = "Время измерения" }) {
                Icon(painterResource(R.drawable.ic_clock), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(localTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
            }
        }
        Text("Контекст", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PressureContext.entries.forEach { item ->
                ContextButton(item, context == item.name, !busy) {
                    manualContext = if (!automaticContext && context == item.name) null else item.name
                    automaticContext = false
                }
            }
        }
        if (attempted) draft.error()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            attempted = true
            if (draft.error() == null) {
                if (draft.unusual()) confirmUnusual = true else onSave(draft)
            }
        }) { Text(if (busy) "Сохранение…" else "Сохранить") }
        if (editing) OutlinedButton(onClick = { deleting = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Удалить запись") }
        TextButton(onClick = onClose, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
    }
    if (picker == "date") dev.dadyarri.medtracker.ui.AppDatePicker(localTime.toLocalDate(), { picker = null }) { day ->
        val updated = day.atTime(localTime.toLocalTime()).format(PressureDraft.TIME_FORMAT)
        if (updated != time) { time = updated; automaticContext = true }
        picker = null
    }
    if (picker == "time") dev.dadyarri.medtracker.ui.AppTimePicker(localTime.hour, localTime.minute, { picker = null }) { hour, minute ->
        val updated = localTime.withHour(hour).withMinute(minute).format(PressureDraft.TIME_FORMAT)
        if (updated != time) { time = updated; automaticContext = true }
        picker = null
    }
    if (deleting) AlertDialog(onDismissRequest = { if (!busy) deleting = false },
        title = { Text("Удалить измерение?") }, text = { Text("Восстановить запись после удаления не получится.") },
        confirmButton = { TextButton(onClick = { deleting = false; onDelete() }, enabled = !busy) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleting = false }, enabled = !busy) { Text("Отмена") } })
    if (confirmUnusual) AlertDialog(onDismissRequest = { confirmUnusual = false },
        title = { Text("Проверьте значения") }, text = { Text("Введены необычные значения. Убедитесь, что они совпадают с показаниями прибора.") },
        confirmButton = { TextButton(onClick = { confirmUnusual = false; onSave(draft) }, enabled = !busy) { Text("Сохранить как есть") } },
        dismissButton = { TextButton(onClick = { confirmUnusual = false }) { Text("Проверить") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextButton(item: PressureContext, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val icon = item.iconResource
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(item.label) } },
        state = rememberTooltipState(),
    ) {
        FilledIconToggleButton(checked = checked, onCheckedChange = { onClick() }, enabled = enabled,
            modifier = Modifier.size(56.dp)) {
            Icon(painterResource(icon), contentDescription = item.label, modifier = Modifier.size(28.dp))
        }
    }
}

// Advance only on appended input; deleting, restoring a draft or opening an edit
// must not unexpectedly move focus. These ranges affect focus, never validation.
private fun completedNumber(previous: String, value: String, twoDigitStart: CharRange, range: IntRange): Boolean =
    value.length > previous.length && value.startsWith(previous) &&
        value.all { it in '0'..'9' } && value.toIntOrNull()?.let { it in range } == true &&
        ((value.length == 2 && value.first() in twoDigitStart) ||
            (value.length == 3 && value.first() in '1'..'2'))

@Composable
private fun NumberInput(label: String, value: String, busy: Boolean, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics { })
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = !busy,
            textStyle = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("—", style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics { })
                    field()
                }
            },
        )
    }
}
