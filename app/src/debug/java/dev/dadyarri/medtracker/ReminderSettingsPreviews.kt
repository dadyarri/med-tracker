package dev.dadyarri.medtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.meals.CycleSettings
import dev.dadyarri.medtracker.meals.GlucoseCycle
import dev.dadyarri.medtracker.reminders.ReminderCard
import dev.dadyarri.medtracker.reminders.ReminderKind
import dev.dadyarri.medtracker.reminders.ReminderSetting
import java.time.LocalDate

@Preview(widthDp = 400, heightDp = 800)
@Preview(widthDp = 400, heightDp = 500, fontScale = 1.5f)
@Composable
fun ReminderSettingsPreview() {
    MaterialTheme {
        Surface {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReminderCard(ReminderSetting(ReminderKind.WEIGHT, weeks = 2, anchor = LocalDate.of(2026, 11, 3)), false, {})
                CycleSettings(GlucoseCycle(startDate = "2026-11-03"), false, {}, {})
            }
        }
    }
}
