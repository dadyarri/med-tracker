package dev.dadyarri.medtracker.measurements

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.dadyarri.medtracker.ui.ContextChoice
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.R
import dev.dadyarri.medtracker.pressure.PressureContext
import dev.dadyarri.medtracker.pressure.PressureDraft
import dev.dadyarri.medtracker.pressure.iconResource
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeasurementEditor(
    id: String, initial: MeasurementDraft, editing: Boolean, busy: Boolean, error: String?,
    onSave: (MeasurementDraft) -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
    meals: List<dev.dadyarri.medtracker.meals.MealEvent> = emptyList(),
) {
    var value by rememberSaveable(id) { mutableStateOf(
        if (initial.type == MeasurementType.GLUCOSE && initial.unit == GlucoseUnit.MG && initial.scaled() != null)
            decimalText(GlucoseRecord("", initial.scaled()!!, "MG", initial.food.name, null, "", 0, 0, 0)
                .valueIn(GlucoseUnit.MMOL).setScale(2, java.math.RoundingMode.HALF_UP))
        else initial.value
    ) }
    var time by rememberSaveable(id) { mutableStateOf(initial.time) }
    var manual by rememberSaveable(id) { mutableStateOf(initial.context) }
    var automatic by rememberSaveable(id) { mutableStateOf(!editing) }
    val unit = GlucoseUnit.MMOL.name
    var food by rememberSaveable(id) { mutableStateOf(initial.food.name) }
    var tag by rememberSaveable(id) { mutableStateOf(initial.tag?.name) }
    var meal by rememberSaveable(id) { mutableStateOf(initial.meal?.name) }
    var additional by rememberSaveable(id) { mutableStateOf(false) }
    var mealMenu by remember { mutableStateOf(false) }
    var mealEventId by rememberSaveable(id) { mutableStateOf(initial.mealEventId) }
    var eventMenu by remember { mutableStateOf(false) }
    val comment = initial.comment
    var picker by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var attempted by rememberSaveable(id) { mutableStateOf(false) }
    var unusual by rememberSaveable(id) { mutableStateOf(false) }
    var deleting by rememberSaveable(id) { mutableStateOf(false) }
    val selected = LocalDateTime.parse(time, PressureDraft.TIME_FORMAT)
    val context = if (automatic) PressureContext.forHour(selected.hour).name else manual
    val draft = MeasurementDraft(initial.type, value, time, context, GlucoseUnit.valueOf(unit),
        FoodContext.valueOf(food), tag?.let(GlucoseTag::valueOf), comment, meal?.let(MealType::valueOf), mealEventId, initial.originalAt)
    val androidContext = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = busy) {}
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${if (editing) "Изменить" else "Записать"} ${if (initial.type == MeasurementType.WEIGHT) "вес" else "глюкозу"}",
            style = MaterialTheme.typography.headlineSmall)
        Text(initial.type.label, style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
        BasicTextField(value = value, onValueChange = { value = it }, enabled = !busy, singleLine = true,
            textStyle = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = initial.type.label },
            decorationBox = { field ->
                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text("—", style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clearAndSetSemantics {})
                    field()
                }
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { keyboard?.hide(); picker = "date" }, enabled = !busy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1.3f).semantics { contentDescription = "Дата измерения" }) {
                Icon(Icons.Default.DateRange, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(time.substringBefore(' '))
            }
            OutlinedButton(onClick = { keyboard?.hide(); picker = "time" }, enabled = !busy,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Время измерения" }) {
                Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(time.substringAfter(' '))
            }
        }
        if (initial.type == MeasurementType.WEIGHT) {
            Text("Контекст", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PressureContext.entries.forEach { item ->
                    ContextChoice(item.iconResource, item.label, context == item.name, !busy) {
                        manual = if (!automatic && context == item.name) null else item.name
                        automatic = false
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().semantics { contentDescription = "Отношение к еде" },
                horizontalArrangement = Arrangement.SpaceBetween) {
                FoodContext.entries.forEach { item ->
                    ContextChoice(item.icon, item.label, food == item.name, !busy) { food = item.name }
                }
            }
            if (food == FoodContext.BEFORE.name || food == FoodContext.AFTER.name) {
                Box {
                    OutlinedButton(onClick = { mealMenu = true }, enabled = !busy) {
                        Text(meal?.let(MealType::valueOf)?.label ?: "Приём пищи")
                        Text(" ▾")
                    }
                    DropdownMenu(expanded = mealMenu, onDismissRequest = { mealMenu = false }) {
                        DropdownMenuItem(text = { Text("Не указан") }, onClick = { meal = null; mealMenu = false })
                        MealType.entries.forEach { item ->
                            DropdownMenuItem(text = { Text(item.label) }, onClick = { meal = item.name; mealMenu = false })
                        }
                    }
                }
            }
            if (food == FoodContext.BEFORE.name || food == FoodContext.AFTER.name) {
                val matchingMeals = meals.filter { event ->
                    java.time.Instant.ofEpochMilli(event.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == selected.toLocalDate() &&
                        (meal == null || event.type == meal)
                }
                val linked = matchingMeals.find { it.id == mealEventId }
                LaunchedEffect(selected.toLocalDate(), meal, meals) {
                    if (meals.any { it.id == mealEventId } && linked == null) mealEventId = null
                }
                Box {
                    TextButton(onClick = { eventMenu = true }, enabled = !busy) {
                        Text(linked?.let { "${MealType.valueOf(it.type).label} · ${PressureDraft.format(it.startedAt)}" } ?: "Связать с едой")
                    }
                    DropdownMenu(expanded = eventMenu, onDismissRequest = { eventMenu = false }) {
                        DropdownMenuItem(text = { Text("Без связи") }, onClick = { mealEventId = null; eventMenu = false })
                        if (matchingMeals.isEmpty()) DropdownMenuItem(
                            text = { Text("Нет подходящих приёмов пищи") }, enabled = false, onClick = {})
                        matchingMeals.forEach { event ->
                            DropdownMenuItem(text = { Text("${MealType.valueOf(event.type).label} · ${PressureDraft.format(event.startedAt)}") },
                                onClick = { mealEventId = event.id; meal = event.type; eventMenu = false })
                        }
                    }
                }
                if (linked != null && food == FoodContext.AFTER.name) {
                    Text("${dev.dadyarri.medtracker.meals.GlucoseCyclePlan.elapsedMinutes(draft.instant()!!, linked)} мин после начала еды",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = { additional = !additional }, enabled = !busy) {
                Text(if (additional) "Свернуть" else "Дополнительно")
            }
            if (additional) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlucoseTag.entries.forEach { item ->
                        FilterChip(selected = tag == item.name, enabled = !busy,
                            onClick = { tag = if (tag == item.name) null else item.name },
                            label = { Text(item.label) })
                    }
                }
            }
        }
        if (attempted) draft.error()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = {
            attempted = true
            if (draft.error() == null) { if (draft.unusual()) unusual = true else onSave(draft) }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Сохранение…" else "Сохранить") }
        if (editing) OutlinedButton(onClick = { deleting = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text("Удалить запись") }
        TextButton(onClick = onClose, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
    }
    if (picker != null) DisposableEffect(picker) {
        fun changed(next: LocalDateTime) {
            val updated = next.format(PressureDraft.TIME_FORMAT)
            if (updated != time) { time = updated; automatic = true }
        }
        val dialog = if (picker == "date") DatePickerDialog(androidContext, { _, y, m, d ->
            changed(LocalDate.of(y, m + 1, d).atTime(selected.toLocalTime()))
        }, selected.year, selected.monthValue - 1, selected.dayOfMonth)
        else TimePickerDialog(androidContext, { _, h, m -> changed(selected.withHour(h).withMinute(m)) },
            selected.hour, selected.minute, DateFormat.is24HourFormat(androidContext))
        dialog.setOnDismissListener { picker = null }
        dialog.show()
        onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
    }
    if (unusual) AlertDialog(onDismissRequest = { if (!busy) unusual = false },
        title = { Text("Проверьте значение") }, text = { Text("Значение необычно. Убедитесь, что число и единицы указаны верно.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { unusual = false; onSave(draft) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { unusual = false }) { Text("Исправить") } })
    if (deleting) AlertDialog(onDismissRequest = { if (!busy) deleting = false },
        title = { Text("Удалить запись?") }, text = { Text("Восстановить удалённую запись нельзя.") },
        confirmButton = { TextButton(enabled = !busy, onClick = onDelete) { Text("Удалить") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { deleting = false }) { Text("Отмена") } })
}
