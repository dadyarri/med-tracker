package dev.dadyarri.medtracker.medications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
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
    val colors = MaterialTheme.colorScheme
    AlertDialog(onDismissRequest = onDismiss, shape = RoundedCornerShape(36.dp),
        containerColor = colors.surfaceContainerLow, title = { Text("Внешний вид") }, text = {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(30.dp))
                .background(Brush.radialGradient(listOf(colors.surface, colors.surfaceContainerHigh))),
                contentAlignment = Alignment.Center) {
                MedicationVisual(value, Modifier.fillMaxSize().padding(20.dp))
            }
            LazyHorizontalGrid(rows = GridCells.Fixed(2),
                state = rememberLazyGridState(initialFirstVisibleItemIndex = PillShape.entries.indexOf(PillShape.valueOf(initial.shape))),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "Формы препарата" }) {
                items(PillShape.entries, key = { it.name }) { type ->
                    AppearanceOption(shape == type.name, "Форма: ${type.label}", enabled,
                        Modifier.width(76.dp).fillMaxHeight(), onClick = {
                            shape = type.name
                            if (!type.supportsScore) score = 0
                            if (type != PillShape.CAPSULE) half = 0
                        }) {
                        if (type == PillShape.NONE) Icon(painterResource(R.drawable.ic_context_unlinked), null, Modifier.size(24.dp))
                        else MedicationVisual(value.copy(shape = type.name, score = 0), Modifier.fillMaxSize().padding(6.dp))
                    }
                }
            }
            if (shape != "NONE") {
                if (shape == "CAPSULE") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Первая половина", "Вторая половина").forEachIndexed { index, title ->
                        AppearanceOption(half == index, title, enabled, Modifier.weight(1f).height(52.dp),
                            onClick = { half = index }) {
                            MedicationVisual(value.copy(shape = "OBLONG", color = if (index == 0) color else second, score = 0),
                                Modifier.size(56.dp))
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0xFFFFFFFF, 0xFFFFDAD6, 0xFFFFDDB3, 0xFFFFFFB0, 0xFFB8E3BE, 0xFFB8DFFF, 0xFFDCC7FF, 0xFF6750A4).forEach { item ->
                        AppearanceColor(item, selectedColor == item, enabled) { choose(item) }
                    }
                    Surface(onClick = { hex = "%06X".format(selectedColor and 0xFFFFFF); custom = true },
                        enabled = enabled, shape = CircleShape, color = colors.surfaceContainerHigh,
                        modifier = Modifier.size(48.dp).semantics { contentDescription = "Свой цвет" }) {
                        Box(contentAlignment = Alignment.Center) { Text("#", style = MaterialTheme.typography.titleLarge) }
                    }
                }
                if (PillShape.valueOf(shape).supportsScore) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Без риски", "Одна", "Крест").forEachIndexed { index, title ->
                        AppearanceOption(score == index, title, enabled, Modifier.weight(1f).height(64.dp),
                            onClick = { score = index }) {
                            MedicationVisual(value.copy(score = index), Modifier.size(56.dp))
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(value) }, enabled = enabled) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
    if (custom) {
        val pastel = pastelMedicationColor(hex)
        AlertDialog(onDismissRequest = { custom = false }, shape = RoundedCornerShape(32.dp), title = { Text("Цвет") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(hex, { hex = it.trim().removePrefix("#").take(6) }, prefix = { Text("#") }, singleLine = true,
                    modifier = Modifier.semantics { contentDescription = "HEX цвета" })
                if (pastel != null) {
                    MedicationVisual(if (half == 1 && shape == "CAPSULE") value.copy(secondColor = pastel)
                        else value.copy(color = pastel), Modifier.fillMaxWidth().height(100.dp))
                    Text("Пастельный · #${"%06X".format(pastel and 0xFFFFFF)}", style = MaterialTheme.typography.labelMedium)
                }
            } }, confirmButton = { TextButton(onClick = {
                pastel?.let(::choose); custom = false
            }, enabled = enabled && pastel != null) { Text("Выбрать") } },
            dismissButton = { TextButton(onClick = { custom = false }) { Text("Отмена") } })
    }
}

@Composable
private fun AppearanceOption(selected: Boolean, description: String, enabled: Boolean, modifier: Modifier,
                             onClick: () -> Unit, content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(22.dp),
        color = if (selected) colors.primaryContainer.copy(alpha = .55f) else colors.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, if (selected) colors.primary.copy(alpha = .65f) else Color.Transparent),
        modifier = modifier.semantics { contentDescription = description; this.selected = selected; role = Role.RadioButton }) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun AppearanceColor(value: Long, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val color = Color(value)
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .5f) else Color.Transparent,
        border = BorderStroke(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        modifier = Modifier.size(48.dp).semantics {
            contentDescription = "Цвет: ${"%06X".format(value and 0xFFFFFF)}"; this.selected = selected; role = Role.RadioButton
        }) {
        Canvas(Modifier.padding(8.dp)) {
            val radius = size.minDimension * .44f
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = .2f), Color.Transparent),
                center + Offset(0f, radius * .2f), radius * 1.12f), radius * 1.12f, center + Offset(0f, radius * .2f))
            drawCircle(Brush.radialGradient(listOf(lerp(color, Color.White, .5f), color, lerp(color, Color.Black, .22f)),
                center - Offset(radius * .5f, radius * .5f), radius * 2), radius)
            drawCircle(Color.White.copy(alpha = .35f), radius, style = androidx.compose.ui.graphics.drawscope.Stroke(.7.dp.toPx()))
        }
    }
}
