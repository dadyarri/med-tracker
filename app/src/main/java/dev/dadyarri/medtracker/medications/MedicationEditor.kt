package dev.dadyarri.medtracker.medications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import dev.dadyarri.medtracker.R

@Composable
internal fun MedicationEditor(initial: Medication, busy: Boolean, error: String?,
                              onSave: (Medication) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var form by rememberSaveable(initial.id) { mutableStateOf(initial.form) }
    var strength by rememberSaveable(initial.id) { mutableStateOf(initial.strength) }
    var unit by rememberSaveable(initial.id) { mutableStateOf(initial.unit) }
    var dose by rememberSaveable(initial.id) { mutableStateOf(initial.usualDose.replace('.', ',')) }
    var instruction by rememberSaveable(initial.id) { mutableStateOf(initial.instruction) }
    var shape by rememberSaveable(initial.id) { mutableStateOf(initial.shape) }
    var score by rememberSaveable(initial.id) { mutableIntStateOf(initial.score) }
    var color by rememberSaveable(initial.id) { mutableLongStateOf(initial.color) }
    var second by rememberSaveable(initial.id) { mutableLongStateOf(initial.secondColor) }
    var visual by rememberSaveable(initial.id) { mutableStateOf(false) }
    var detail by rememberSaveable(initial.id) { mutableStateOf(initial.form.isNotBlank() || initial.strength.isNotBlank() || initial.instruction.isNotBlank()) }
    val value = initial.copy(name = name, form = form, strength = strength, unit = unit, usualDose = dose,
        instruction = instruction, shape = shape, score = score, color = color, secondColor = second)
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Препарат", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            MedicationVisual(value, Modifier.size(64.dp))
        }
        OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MedicationNumber("Доза", dose, !busy, Modifier.weight(1f)) { dose = it }
            OutlinedTextField(unit, { unit = it }, label = { Text("Единица") }, singleLine = true, enabled = !busy, modifier = Modifier.weight(1f))
        }
        TextButton(onClick = { detail = !detail }) { Text("Форма и инструкция") }
        if (detail) {
            OutlinedTextField(form, { form = it }, label = { Text("Форма выпуска") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(strength, { strength = it }, label = { Text("Сила / концентрация") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(instruction, { instruction = it }, label = { Text("Инструкция") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = { visual = !visual }) { Text("Внешний вид") }
        if (visual) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillShape.entries.forEach { type ->
                    FilterChip(selected = shape == type.name, enabled = !busy,
                        onClick = { shape = type.name; if (type in listOf(PillShape.NONE, PillShape.CAPSULE)) score = 0 },
                        modifier = Modifier.semantics { contentDescription = type.label },
                        label = {
                            if (type == PillShape.NONE) Icon(painterResource(R.drawable.ic_context_unlinked), null, Modifier.size(24.dp))
                            else MedicationVisual(value.copy(shape = type.name, score = 0), Modifier.size(32.dp))
                        })
                }
            }
            if (shape != "NONE") {
                MedicationColor(color, !busy, "Цвет") { color = it }
                if (shape == "CAPSULE") MedicationColor(second, !busy, "Вторая половина") { second = it }
                else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Без риски", "Одна", "Крест").forEachIndexed { index, title ->
                        FilterChip(selected = score == index, enabled = !busy, onClick = { score = index }, label = { Text(title) })
                    }
                }
                Text("Визуальная памятка", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(value) }, enabled = !busy && name.isNotBlank() && unit.isNotBlank() && doseValue(dose) != null,
            modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        TextButton(onClick = onClose, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
    }
}

@Composable
private fun MedicationColor(value: Long, enabled: Boolean, title: String, onChange: (Long) -> Unit) {
    var custom by rememberSaveable(title, value) { mutableStateOf("%06X".format(value and 0xFFFFFF)) }
    Text(title, style = MaterialTheme.typography.labelMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0xFFFFFFFF, 0xFFFFDAD6, 0xFFFFDDB3, 0xFFFFFFB0, 0xFFB8E3BE, 0xFFB8DFFF, 0xFFDCC7FF, 0xFF6750A4).forEach { color ->
            FilterChip(selected = value == color, onClick = { onChange(color) }, enabled = enabled,
                label = { Surface(color = Color(color), shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(24.dp).semantics { contentDescription = "$title: ${"%06X".format(color and 0xFFFFFF)}" }) {} })
        }
    }
    OutlinedTextField(custom, { text ->
        custom = text.take(6)
        if (custom.matches(Regex("[0-9a-fA-F]{6}"))) onChange(0xFF000000 or custom.toLong(16))
    }, label = { Text("HEX") }, prefix = { Text("#") }, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
}
