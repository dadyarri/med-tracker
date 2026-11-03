package dev.dadyarri.medtracker

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.reminders.*

private fun sampleReminders() = listOf(
    ReminderOccurrence("p", "PRESSURE", "r", "Давление", 1793696400000, 1793696400000, 1793703600000, "RINGING", "g"),
    ReminderOccurrence("g", "GLUCOSE", "r", "Глюкоза · до еды", 1793696400000, 1793696400000, 1793703600000, "RINGING", "g",
        food = "BEFORE", meal = "LUNCH"),
)
@Preview(widthDp = 400, heightDp = 800)
@Preview(widthDp = 400, heightDp = 500, fontScale = 1.5f)
@Composable
fun ReminderAlarmPreview() { MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) { ReminderAlarmScreen(sampleReminders(), false, {}, { _, _ -> }) } }

@Preview(widthDp = 400, heightDp = 400)
@Preview(widthDp = 400, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PlannedCardsPreview() {
    MaterialTheme(colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                sampleReminders().forEach { PlannedReminderCard(it) {} }
            }
        }
    }
}
