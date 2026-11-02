package dev.dadyarri.medtracker.meals

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
fun CycleSettings(cycle: GlucoseCycle?, busy: Boolean, onToggle: () -> Unit, onSave: (GlucoseCycle) -> Unit) {
    val current = cycle ?: GlucoseCycle(startDate = LocalDate.now().toString())
    var after by rememberSaveable(current.afterMinutes) { mutableStateOf(current.afterMinutes.toString()) }
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Контроль глюкозы", style = MaterialTheme.typography.titleLarge)
        Text("День 1: натощак и после завтрака\nДень 2: до и после обеда\nДень 3: до и после ужина",
            style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onToggle, enabled = !busy) { Text(if (current.active) "Приостановить" else "Начать с дня 1") }
        listOf("Завтрак" to current.breakfastMinute, "Обед" to current.lunchMinute, "Ужин" to current.dinnerMinute).forEach { (name, minute) ->
            OutlinedButton(onClick = { picker = name }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("$name · %02d:%02d".format(minute / 60, minute % 60))
            }
        }
        OutlinedTextField(value = after, onValueChange = { after = it }, label = { Text("После начала еды, минут") },
            singleLine = true, enabled = !busy, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        Button(onClick = { onSave(current.copy(afterMinutes = after.toInt())) },
            enabled = !busy && after.toIntOrNull() in 1..1440) { Text("Сохранить") }
    }
    if (picker != null) DisposableEffect(picker) {
        val minute = when (picker) { "Завтрак" -> current.breakfastMinute; "Обед" -> current.lunchMinute; else -> current.dinnerMinute }
        val dialog = TimePickerDialog(context, { _, h, m ->
            val value = h * 60 + m
            onSave(when (picker) {
                "Завтрак" -> current.copy(breakfastMinute = value)
                "Обед" -> current.copy(lunchMinute = value)
                else -> current.copy(dinnerMinute = value)
            })
        }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(context))
        dialog.setOnDismissListener { picker = null }; dialog.show()
        onDispose { dialog.setOnDismissListener(null); dialog.dismiss() }
    }
}
