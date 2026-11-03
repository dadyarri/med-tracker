package dev.dadyarri.medtracker.meals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.measurements.*

@Composable
internal fun CycleDayDialog(day: Int, initial: CycleDay, busy: Boolean,
                            onDismiss: () -> Unit, onSave: (CycleDay) -> Unit) {
    var meal by rememberSaveable(day, initial) { mutableStateOf(initial.meal.name) }
    var before by rememberSaveable(day, initial) { mutableStateOf(initial.before) }
    var after by rememberSaveable(day, initial) { mutableStateOf(initial.after) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("День $day") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                MealType.entries.filter { it != MealType.SNACK }.forEach { type ->
                    FilterChip(selected = meal == type.name, onClick = { meal = type.name }, enabled = !busy,
                        label = { Icon(painterResource(type.icon), type.label, Modifier.size(24.dp)) })
                }
            }
            Column {
                listOf(FoodContext.FASTING, FoodContext.BEFORE, FoodContext.NONE).forEach { context ->
                    FilterChip(selected = before == context.name, onClick = { before = context.name }, enabled = !busy,
                        leadingIcon = { Icon(painterResource(context.icon), null, Modifier.size(20.dp)) },
                        label = { Text(if (context == FoodContext.NONE) "Только после еды" else context.label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(FoodContext.AFTER.icon), null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("После еды", Modifier.weight(1f))
                Switch(checked = after, onCheckedChange = { after = it }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "После еды: день $day" })
            }
        }
    }, confirmButton = {
        TextButton(enabled = !busy && (before != "NONE" || after), onClick = {
            onSave(CycleDay(MealType.valueOf(meal), before, after))
        }) { Text("Сохранить") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
