package dev.dadyarri.medtracker.meals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import dev.dadyarri.medtracker.measurements.*
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
import dev.dadyarri.medtracker.R
import java.time.LocalDate

@Composable
fun CycleSettings(cycle: GlucoseCycle?, busy: Boolean, onToggle: () -> Unit, onSave: (GlucoseCycle) -> Unit) {
    val current = cycle ?: GlucoseCycle(startDate = LocalDate.now().toString())
    var after by rememberSaveable(current.afterMinutes) { mutableStateOf(current.afterMinutes.toString()) }
    var interval by rememberSaveable { mutableStateOf(false) }
    var editingDay by rememberSaveable { mutableStateOf<Int?>(null) }
    Card(shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_glucose), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Глюкоза", style = MaterialTheme.typography.titleMedium)
                    Text(if (current.active) "Цикл · день ${current.day(LocalDate.now()) ?: 1} из 3" else "Приостановлено",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = current.active, enabled = !busy, onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics { contentDescription = "Напоминания: Глюкоза" })
            }
            (1..3).forEach { day ->
                val configuration = current.configuration(day)!!
                val minute = when (configuration.meal) {
                    MealType.BREAKFAST -> current.breakfastMinute
                    MealType.LUNCH -> current.lunchMinute
                    else -> current.dinnerMinute
                }
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { editingDay = day }
                    .semantics { contentDescription = "Настроить день $day" }, verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Text("$day", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
                    }
                    Icon(painterResource(configuration.meal.icon), configuration.meal.label, Modifier.size(24.dp))
                    val title = buildList {
                        if (configuration.before != "NONE") add(FoodContext.valueOf(configuration.before).label)
                        if (configuration.after) add("После еды")
                    }.joinToString(" · ")
                    Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (configuration.before != "NONE") {
                        val before = Math.floorMod(minute - 30, 1440)
                        Text("%02d:%02d".format(before / 60, before % 60),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { contentDescription = "Время до еды: день $day" })
                    }
                }
            }
            OutlinedButton(onClick = { interval = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Через ${current.afterMinutes} мин после еды")
            }
        }
    }
    editingDay?.let { day ->
        CycleDayDialog(day, current.configuration(day)!!, busy,
            onDismiss = { editingDay = null },
            onSave = { onSave(current.withDay(day, it)); editingDay = null })
    }
    if (interval) AlertDialog(onDismissRequest = { interval = false }, title = { Text("После начала еды") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicTextField(value = after, onValueChange = { after = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Интервал после еды" })
                Text("минут", style = MaterialTheme.typography.labelMedium)
                if (after.toIntOrNull() !in 1..1440) Text("От 1 до 1440 минут", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = { TextButton(enabled = !busy && after.toIntOrNull() in 1..1440, onClick = {
            onSave(current.copy(afterMinutes = after.toInt())); interval = false
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = { interval = false }) { Text("Отмена") } })

}
