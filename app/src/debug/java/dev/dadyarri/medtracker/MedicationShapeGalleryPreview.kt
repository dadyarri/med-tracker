package dev.dadyarri.medtracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.medications.*

@Preview(widthDp = 400, heightDp = 900)
@Composable
fun MedicationShapeGalleryPreview() {
    MaterialTheme { Surface {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillShape.entries.chunked(3).forEach { shapes ->
                Row(Modifier.fillMaxWidth()) {
                    shapes.forEach { shape ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            MedicationVisual(Medication("m", "Препарат", shape = shape.name,
                                score = if (shape.supportsScore) 1 else 0, color = 0xFFB8DFFF,
                                secondColor = 0xFFFFDAD6), Modifier.size(92.dp))
                            Text(shape.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    repeat(3 - shapes.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    } }
}

@Preview(widthDp = 400, heightDp = 500)
@Composable
fun MedicationGroovePreview() {
    MaterialTheme { Surface {
        Column {
            MedicationVisual(Medication("round", "Препарат", shape = "ROUND", score = 2,
                color = 0xFFFFFFB0), Modifier.fillMaxWidth().height(240.dp))
            MedicationVisual(Medication("capsule", "Препарат", shape = "CAPSULE",
                color = 0xFFB8DFFF, secondColor = 0xFFFFDAD6), Modifier.fillMaxWidth().height(240.dp))
        }
    } }
}
