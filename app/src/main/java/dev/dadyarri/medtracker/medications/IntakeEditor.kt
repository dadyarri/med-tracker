package dev.dadyarri.medtracker.medications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun IntakeEditor(medication: Medication, initial: MedicationIntake, editing: Boolean,
                          regimens: List<MedicationRegimen>, busy: Boolean, error: String?,
                          onSave: (MedicationIntake) -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    var dose by rememberSaveable(initial.id) { mutableStateOf(initial.dose.replace('.', ',')) }
    var at by rememberSaveable(initial.id) { mutableLongStateOf(initial.takenAt) }
    var regimen by rememberSaveable(initial.id) { mutableStateOf(initial.regimenId) }
    var deleting by rememberSaveable(initial.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MedicationVisual(medication, Modifier.size(64.dp))
        Text(medication.name, style = MaterialTheme.typography.headlineSmall)
        MedicationNumber("Доза · ${initial.unit}", dose, !busy) { dose = it }
        MedicationDateTime(at, !busy, "Приём") { at = it }
        if (regimens.isNotEmpty()) {
            MedicationChoice(regimen, listOf<String?>(null) + regimens.map { it.id }, { id ->
                regimens.find { it.id == id }?.let { "${RegimenKind.valueOf(it.kind).label} · ${dev.dadyarri.medtracker.pressure.PressureDraft.format(it.startsAt).substringBefore(' ')}" }
                    ?: "Без назначения"
            }, !busy) { regimen = it }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(initial.copy(dose = dose, takenAt = at, regimenId = regimen)) },
            enabled = !busy && doseValue(dose) != null, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        if (editing) OutlinedButton(onClick = { deleting = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Удалить запись") }
        TextButton(onClick = onClose, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
    }
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Удалить запись?") },
        confirmButton = { TextButton(onClick = onDelete, enabled = !busy) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } })
}
