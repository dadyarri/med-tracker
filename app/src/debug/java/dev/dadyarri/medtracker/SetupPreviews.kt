package dev.dadyarri.medtracker

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Preview(showBackground = true, widthDp = 400, heightDp = 500, fontScale = 1.5f)
@Composable
private fun PermissionWizardPreview() {
    MaterialTheme {
        PermissionWizard(List(5) { false }, false, null, {}, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Composable
private fun EmptyHomePreview() {
    MaterialTheme { EmptyHome() }
}
