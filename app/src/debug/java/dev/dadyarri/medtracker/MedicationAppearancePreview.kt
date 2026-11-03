package dev.dadyarri.medtracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.dadyarri.medtracker.medications.*

@Preview(widthDp = 400, heightDp = 800)
@Composable
fun MedicationAppearancePreview() {
    MaterialTheme { Surface {
        MedicationAppearanceDialog(Medication("m", "Препарат", shape = "CAPSULE",
            color = 0xFFB8DFFF, secondColor = 0xFFFFDAD6), true, {}, {})
    } }
}
