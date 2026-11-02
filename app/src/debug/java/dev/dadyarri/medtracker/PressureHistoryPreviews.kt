package dev.dadyarri.medtracker

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.pressure.PressureRecordList
import dev.dadyarri.medtracker.pressure.PressureRecord
import java.time.Instant

@Preview(showBackground = true, widthDp = 400, heightDp = 800)
@Preview(showBackground = true, widthDp = 400, heightDp = 500, fontScale = 1.5f)
@Preview(showBackground = true, widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PressureHistoryPreview() {
    val at = Instant.parse("2026-11-03T05:45:00Z").toEpochMilli()
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            Column(Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("История", style = MaterialTheme.typography.headlineMedium)
                PressureRecordList(listOf(
                    PressureRecord("one", 120, 80, 72, "MORNING", "", at, at, at),
                    PressureRecord("two", 118, 76, null, null, "", at - 86_400_000, at, at),
                ), groupByDate = true) {}
            }
        }
    }
}
