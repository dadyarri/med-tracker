package dev.dadyarri.medtracker.medications

import androidx.compose.foundation.BorderStroke
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
internal fun MedicationAppearanceDialog(initial: Medication, enabled: Boolean, onDismiss: () -> Unit,
                                        onConfirm: (Medication) -> Unit) {
    var shape by rememberSaveable(initial.id) { mutableStateOf(initial.shape) }
    var score by rememberSaveable(initial.id) { mutableIntStateOf(initial.score) }
    var color by rememberSaveable(initial.id) { mutableLongStateOf(initial.color) }
    var second by rememberSaveable(initial.id) { mutableLongStateOf(initial.secondColor) }
    var half by rememberSaveable { mutableIntStateOf(0) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var hex by rememberSaveable { mutableStateOf("") }
    val selectedColor = if (half == 1 && shape == "CAPSULE") second else color
    fun choose(value: Long) { if (half == 1 && shape == "CAPSULE") second = value else color = value }
    val value = initial.copy(shape = shape, score = score, color = color, secondColor = second)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Внешний вид") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()) {
                Box(contentAlignment = Alignment.Center) { MedicationVisual(value, Modifier.padding(12.dp).size(112.dp)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillShape.entries.forEach { type ->
                    FilterChip(selected = shape == type.name, enabled = enabled,
                        onClick = {
                            shape = type.name
                            if (type in listOf(PillShape.NONE, PillShape.CAPSULE)) score = 0
                            if (type != PillShape.CAPSULE) half = 0
                        },
                        modifier = Modifier.semantics { contentDescription = "Форма: ${type.label}" },
                        label = {
                            if (type == PillShape.NONE) Icon(painterResource(R.drawable.ic_context_unlinked), null, Modifier.size(24.dp))
                            else MedicationVisual(value.copy(shape = type.name, score = 0), Modifier.size(32.dp))
                        })
                }
            }
            if (shape != "NONE") {
                if (shape == "CAPSULE") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Первая половина", "Вторая половина").forEachIndexed { index, title ->
                        FilterChip(selected = half == index, onClick = { half = index }, enabled = enabled,
                            label = { Surface(color = Color(if (index == 0) color else second),
                                shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.size(28.dp).semantics { contentDescription = title }) {} })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0xFFFFFFFF, 0xFFFFDAD6, 0xFFFFDDB3, 0xFFFFFFB0, 0xFFB8E3BE, 0xFFB8DFFF, 0xFFDCC7FF, 0xFF6750A4).forEach { item ->
                        Surface(onClick = { choose(item) }, enabled = enabled, shape = MaterialTheme.shapes.large,
                            color = Color(item), border = BorderStroke(if (selectedColor == item) 3.dp else 1.dp,
                                if (selectedColor == item) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            modifier = Modifier.size(40.dp).semantics { contentDescription = "Цвет: ${"%06X".format(item and 0xFFFFFF)}" }) {}
                    }
                    OutlinedButton(onClick = { hex = "%06X".format(selectedColor and 0xFFFFFF); custom = true }, enabled = enabled,
                        contentPadding = PaddingValues(0.dp), modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)
                            .semantics { contentDescription = "Свой цвет" }) { Text("#") }
                }
                if (shape != "CAPSULE") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Без риски", "Одна", "Крест").forEachIndexed { index, title ->
                        FilterChip(selected = score == index, enabled = enabled, onClick = { score = index },
                            label = { Text(title) })
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = enabled) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
    if (custom) AlertDialog(onDismissRequest = { custom = false }, title = { Text("Цвет") },
        text = { TextField(hex, { hex = it.take(6) }, prefix = { Text("#") }, singleLine = true,
            modifier = Modifier.semantics { contentDescription = "HEX цвета" }) },
        confirmButton = { TextButton(onClick = {
            choose(0xFF000000 or hex.toLong(16)); custom = false
        }, enabled = enabled && hex.matches(Regex("[0-9a-fA-F]{6}"))) { Text("Выбрать") } },
        dismissButton = { TextButton(onClick = { custom = false }) { Text("Отмена") } })
}
