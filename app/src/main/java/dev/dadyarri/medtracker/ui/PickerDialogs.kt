package dev.dadyarri.medtracker.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTimePicker(hour: Int, minute: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(hour, minute, android.text.format.DateFormat.is24HourFormat(LocalContext.current))
    var input by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Время") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (input) TimeInput(state) else TimePicker(state)
                TextButton(onClick = { input = !input }) { Text(if (input) "Часы" else "Ввести время") }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Выбрать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDatePicker(date: LocalDate, onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = state.selectedDateMillis != null, onClick = {
            onConfirm(Instant.ofEpochMilli(state.selectedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate())
        }) { Text("Выбрать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }) {
        DatePicker(state, showModeToggle = true)
    }
}
