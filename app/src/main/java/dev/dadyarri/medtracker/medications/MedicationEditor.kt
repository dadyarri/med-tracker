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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.R

@Composable
internal fun MedicationEditor(initial: Medication, busy: Boolean, error: String?,
                              onSave: (Medication) -> Unit, onClose: () -> Unit) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var strength by rememberSaveable(initial.id) { mutableStateOf(initial.strength) }
    var unit by rememberSaveable(initial.id) { mutableStateOf(initial.unit) }
    var dose by rememberSaveable(initial.id) { mutableStateOf(initial.usualDose.replace('.', ',')) }
    var instruction by rememberSaveable(initial.id) { mutableStateOf(initial.instruction) }
    var shape by rememberSaveable(initial.id) { mutableStateOf(initial.shape) }
    var score by rememberSaveable(initial.id) { mutableIntStateOf(initial.score) }
    var color by rememberSaveable(initial.id) { mutableLongStateOf(initial.color) }
    var second by rememberSaveable(initial.id) { mutableLongStateOf(initial.secondColor) }
    var visual by rememberSaveable(initial.id) { mutableStateOf(false) }
    var detail by rememberSaveable(initial.id) { mutableStateOf(initial.instruction.isNotBlank()) }
    var units by remember { mutableStateOf(false) }
    var customUnit by rememberSaveable { mutableStateOf(false) }
    var unitDraft by rememberSaveable { mutableStateOf(initial.unit) }
    val value = initial.copy(name = name, strength = strength, unit = unit, usualDose = dose,
        instruction = instruction, shape = shape, score = score, color = color, secondColor = second)
    val fieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Препарат", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Surface(onClick = { visual = true }, enabled = !busy, shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.semantics { contentDescription = "Внешний вид" }) {
                MedicationVisual(value, Modifier.padding(8.dp).size(64.dp))
            }
        }
        TextField(name, { name = it }, label = { Text("Название") }, singleLine = true, enabled = !busy,
            textStyle = MaterialTheme.typography.titleLarge, shape = MaterialTheme.shapes.large,
            colors = fieldColors, modifier = Modifier.fillMaxWidth())
        TextField(strength, { strength = it }, label = { Text("Концентрация", style = MaterialTheme.typography.labelMedium) }, singleLine = true,
            enabled = !busy, shape = MaterialTheme.shapes.large, colors = fieldColors, modifier = Modifier.fillMaxWidth())
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MedicationNumber("Доза", dose, !busy, Modifier.weight(1f)) { dose = it }
                Box {
                    TextButton(onClick = { units = true }, enabled = !busy,
                        modifier = Modifier.widthIn(min = 64.dp, max = 140.dp).semantics { contentDescription = "Единица дозы" }) {
                        Text(unit, style = MaterialTheme.typography.titleLarge)
                    }
                    DropdownMenu(units, { units = false }) {
                        listOf("табл.", "капс.", "мл", "кап.", "г", "мг", "доз.").forEach { item ->
                            DropdownMenuItem(text = { Text(item) }, onClick = { unit = item; units = false })
                        }
                        DropdownMenuItem(text = { Text("Другая…") }, onClick = {
                            units = false; unitDraft = unit; customUnit = true
                        })
                    }
                }
            }
        }
        TextButton(onClick = { detail = !detail }, enabled = !busy) {
            Icon(painterResource(R.drawable.ic_instructions), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Инструкция")
        }
        if (detail) TextField(instruction, { instruction = it }, label = { Text("Инструкция") }, enabled = !busy,
            shape = MaterialTheme.shapes.large, colors = fieldColors, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(value) }, enabled = !busy && name.isNotBlank() && unit.isNotBlank() && doseValue(dose) != null,
            modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        TextButton(onClick = onClose, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
    }
    if (visual) MedicationAppearanceDialog(value, !busy, { visual = false }) { appearance ->
        shape = appearance.shape; score = appearance.score; color = appearance.color; second = appearance.secondColor
        visual = false
    }
    if (customUnit) AlertDialog(onDismissRequest = { customUnit = false }, title = { Text("Единица дозы") },
        text = { TextField(unitDraft, { unitDraft = it }, singleLine = true,
            modifier = Modifier.semantics { contentDescription = "Своя единица" }) },
        confirmButton = { TextButton(onClick = { unit = unitDraft.trim(); customUnit = false }, enabled = !busy && unitDraft.isNotBlank()) { Text("Выбрать") } },
        dismissButton = { TextButton(onClick = { customUnit = false }) { Text("Отмена") } })
}
