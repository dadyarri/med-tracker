package dev.dadyarri.medtracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.dadyarri.medtracker.pressure.PressureEditor

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Preview(showBackground = true, widthDp = 400, heightDp = 500, fontScale = 1.5f)
@Composable
private fun PressureEditorPreview() {
    MaterialTheme {
        PressureEditor("preview", listOf("", "", "", "03.11.2026 08:45", "", ""), false, false, null, {}, {}, {})
    }
}
