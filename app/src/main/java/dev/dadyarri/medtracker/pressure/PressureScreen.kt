package dev.dadyarri.medtracker.pressure

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dadyarri.medtracker.EmptyHome
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PressureHome(model: PressureViewModel = koinViewModel()) {
    val records by model.records.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var initial by rememberSaveable { mutableStateOf<List<String>?>(null) }

    fun open(record: PressureRecord?) {
        val draft = record?.let(PressureDraft::from)
            ?: PressureDraft(time = PressureDraft.format(System.currentTimeMillis()))
        initial = listOf(draft.systolic, draft.diastolic, draft.pulse, draft.time, draft.context.orEmpty(), draft.comment)
        editing = record != null
        editorId = record?.id ?: UUID.randomUUID().toString()
        model.error.value = null
    }
    EmptyHome { today ->
        val visible = records?.filter { !today || Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Button(onClick = { open(null) }, modifier = Modifier.fillMaxWidth()) { Text("Записать давление") }
            if (editorId == null) error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                visible == null -> CircularProgressIndicator()
                visible.isEmpty() -> Text(if (today) "Сегодня измерений давления нет" else "Измерений давления пока нет", Modifier.padding(vertical = 24.dp))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                    items(visible, key = { it.id }) { row ->
                        Card(Modifier.fillMaxWidth().clickable { open(row) }) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${row.systolic}/${row.diastolic} мм рт. ст.", style = MaterialTheme.typography.titleLarge)
                                row.pulse?.let { Text("Пульс: $it уд/мин") }
                                Text(PressureDraft.format(row.measuredAt))
                                row.context?.let { value -> PressureContext.entries.find { it.name == value }?.let { Text(it.label) } }
                                if (row.comment.isNotBlank()) Text(row.comment)
                            }
                        }
                    }
                }
            }
        }
    }
    val id = editorId
    val values = initial
    if (id != null && values != null) {
        val close = { editorId = null; model.error.value = null }
        ModalBottomSheet(
            onDismissRequest = { if (!busy) close() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            sheetGesturesEnabled = !busy,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy),
        ) {
            PressureEditor(id, values, editing, busy, error,
                onSave = { model.save(id, editing, it, close) },
                onDelete = { model.delete(id, close) }, onClose = close)
        }
    }
}

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
    val androidContext = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var comment by rememberSaveable(id) { mutableStateOf(initial[5]) }
    var deleting by rememberSaveable(id) { mutableStateOf(false) }
    var confirmUnusual by rememberSaveable(id) { mutableStateOf(false) }
    var attempted by rememberSaveable(id) { mutableStateOf(false) }
    val diastolicFocus = remember { FocusRequester() }
    val pulseFocus = remember { FocusRequester() }
    val timeFocus = remember { FocusRequester() }
    val draft = PressureDraft(systolic, diastolic, pulse, time, context, comment)
    BackHandler(enabled = busy) { }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (editing) "Изменить давление" else "Записать давление", style = MaterialTheme.typography.headlineSmall)
        NumberInput("Верхнее, мм рт. ст.", systolic, busy) { value ->
            val advance = completedNumber(systolic, value, '7'..'9', 70..250)
            systolic = value
            if (advance) diastolicFocus.requestFocus()
        }
        NumberInput("Нижнее, мм рт. ст.", diastolic, busy, Modifier.focusRequester(diastolicFocus)) { value ->
            val advance = completedNumber(diastolic, value, '4'..'9', 40..150)
            diastolic = value
            if (advance) pulseFocus.requestFocus()
        }
        NumberInput("Пульс, уд/мин · необязательно", pulse, busy, Modifier.focusRequester(pulseFocus)) { value ->
            val advance = completedNumber(pulse, value, '3'..'9', 30..220)
            pulse = value
            if (advance) {
                timeFocus.requestFocus()
                keyboard?.hide()
            }
        }
        Text("Дата и время измерения", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { keyboard?.hide(); picker = "date" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("Дата: " + localTime.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu")))
        }
        OutlinedButton(onClick = { keyboard?.hide(); picker = "time" }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().focusRequester(timeFocus).semantics { contentDescription = "Время измерения" }) {
            Text("Время: " + localTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
        }
        Text("Контекст · необязательно", style = MaterialTheme.typography.titleSmall)
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressureContext.entries.take(2).forEach { item ->
                    FilterChip(context == item.name, {
                        manualContext = if (!automaticContext && context == item.name) null else item.name
                        automaticContext = false
                    }, label = { Text(item.label) }, enabled = !busy)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressureContext.entries.drop(2).forEach { item ->
                    FilterChip(context == item.name, {
                        manualContext = if (!automaticContext && context == item.name) null else item.name
                        automaticContext = false
                    }, label = { Text(item.label) }, enabled = !busy)
                }
            }
        }
        OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий · необязательно") },
            enabled = !busy, modifier = Modifier.fillMaxWidth())
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
    if (picker != null) {
        DisposableEffect(picker) {
            val selected = localTime
            val dialog = if (picker == "date") {
                DatePickerDialog(androidContext, { _, year, month, day ->
                    val updated = LocalDate.of(year, month + 1, day).atTime(selected.toLocalTime()).format(PressureDraft.TIME_FORMAT)
                    if (updated != time) {
                        time = updated
                        automaticContext = true
                    }
                }, selected.year, selected.monthValue - 1, selected.dayOfMonth)
            } else {
                TimePickerDialog(androidContext, { _, hour, minute ->
                    val updated = selected.withHour(hour).withMinute(minute).format(PressureDraft.TIME_FORMAT)
                    if (updated != time) {
                        time = updated
                        automaticContext = true
                    }
                }, selected.hour, selected.minute, DateFormat.is24HourFormat(androidContext))
            }
            dialog.setOnDismissListener { picker = null }
            dialog.show()
            onDispose {
                dialog.setOnDismissListener(null)
                dialog.dismiss()
            }
        }
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

// Advance only on appended input; deleting, restoring a draft or opening an edit
// must not unexpectedly move focus. These ranges affect focus, never validation.
private fun completedNumber(previous: String, value: String, twoDigitStart: CharRange, range: IntRange): Boolean =
    value.length > previous.length && value.startsWith(previous) &&
        value.all { it in '0'..'9' } && value.toIntOrNull()?.let { it in range } == true &&
        ((value.length == 2 && value.first() in twoDigitStart) ||
            (value.length == 3 && value.first() in '1'..'2'))

@Composable
private fun NumberInput(label: String, value: String, busy: Boolean, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = modifier.fillMaxWidth())
}
