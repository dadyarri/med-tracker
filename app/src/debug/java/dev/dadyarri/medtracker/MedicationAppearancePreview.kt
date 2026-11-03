package dev.dadyarri.medtracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.dadyarri.medtracker.medications.*

@Preview(widthDp = 400, heightDp = 800)
@Preview(widthDp = 400, heightDp = 800, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun MedicationAppearancePreview() {
    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme())
        androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()) { Surface {
        MedicationAppearanceDialog(Medication("m", "Препарат", shape = "CAPSULE",
            color = 0xFFB8DFFF, secondColor = 0xFFFFDAD6), true, {}, {})
    } }
}
