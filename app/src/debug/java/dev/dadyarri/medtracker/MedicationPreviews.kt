package dev.dadyarri.medtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.medications.*

@Preview(widthDp = 400, heightDp = 650)
@Preview(widthDp = 400, heightDp = 650, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES, fontScale = 1.5f)
@Composable
fun MedicationCardsPreview() {
    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MedicationCard(Medication("m", "Препарат", form = "Капсулы", strength = "10 мг", shape = "CAPSULE",
                    color = 0xFFB8DFFF, secondColor = 0xFFFFDAD6)) {}
                MedicationCard(Medication("n", "Другой препарат", usualDose = "0.5", shape = "ROUND", score = 2, color = 0xFFFFFFB0)) {}
            }
        }
    }
}

@Preview(widthDp = 400, heightDp = 850)
@Composable
fun MedicationEditorPreview() {
    MaterialTheme { Surface { MedicationEditor(Medication("m", "Препарат", shape = "CAPSULE",
        color = 0xFFB8DFFF, secondColor = 0xFFFFDAD6), false, null, {}, {}) } }
}
