package dev.dadyarri.medtracker.medications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.R
import dev.dadyarri.medtracker.ui.AppDatePicker
import dev.dadyarri.medtracker.ui.AppTimePicker
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
internal fun MedicationNumber(label: String, value: String, enabled: Boolean,
                              modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(value, onChange, enabled = enabled, singleLine = true,
            textStyle = MaterialTheme.typography.displaySmall.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = label })
    }
}

@Composable
internal fun MedicationDateTime(value: Long, enabled: Boolean, label: String, includeTime: Boolean = true,
                                onChange: (Long) -> Unit) {
    var picker by rememberSaveable(label) { mutableStateOf<String?>(null) }
    val selected = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picker = "date" }, enabled = enabled, modifier = Modifier.weight(1.3f)) {
            Icon(painterResource(R.drawable.ic_calendar), "$label: дата", Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(selected.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")))
        }
        if (includeTime) OutlinedButton(onClick = { picker = "time" }, enabled = enabled, modifier = Modifier.weight(1f)) {
            Icon(painterResource(R.drawable.ic_clock), "$label: время", Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(selected.format(DateTimeFormatter.ofPattern("HH:mm")))
        }
    }
    if (picker == "date") AppDatePicker(selected.toLocalDate(), { picker = null }) { date ->
        onChange(date.atTime(selected.toLocalTime()).atZone(selected.zone).toInstant().toEpochMilli()); picker = null
    }
    if (picker == "time") AppTimePicker(selected.hour, selected.minute, { picker = null }) { h, m ->
        onChange(selected.withHour(h).withMinute(m).toInstant().toEpochMilli()); picker = null
    }
}

@Composable
internal fun <T> MedicationChoice(value: T, values: List<T>, label: (T) -> String, enabled: Boolean,
                                  onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled) { Text(label(value)) }
        DropdownMenu(expanded, { expanded = false }) {
            values.forEach { item -> DropdownMenuItem(text = { Text(label(item)) },
                onClick = { expanded = false; onChange(item) }) }
        }
    }
}
