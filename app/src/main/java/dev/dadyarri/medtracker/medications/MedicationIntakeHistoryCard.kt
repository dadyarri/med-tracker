package dev.dadyarri.medtracker.medications

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.measurements.JournalEntry
import dev.dadyarri.medtracker.ui.MeasurementCardFrame

@Composable
internal fun MedicationIntakeHistoryCard(entry: JournalEntry.Intake, onClick: () -> Unit) {
    MeasurementCardFrame("Приём: ${entry.medication.name}", entry.at, emptyList(), onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MedicationVisual(entry.medication, Modifier.size(48.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.medication.name, style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(entry.record.dose.replace('.', ','), style = MaterialTheme.typography.displaySmall)
                Text(entry.record.unit, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
