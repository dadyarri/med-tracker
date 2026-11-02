package dev.dadyarri.medtracker.reminders

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import dev.dadyarri.medtracker.meals.CycleSettings
import dev.dadyarri.medtracker.meals.GlucoseCycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReminderSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun materialCalendarCancelDoesNotSaveAndConfirmKeepsCalendarDate() {
        val initial = ReminderSetting(ReminderKind.WEIGHT, anchor = LocalDate.of(2026, 11, 3))
        var saved: ReminderSetting? = null
        compose.setContent { MaterialTheme { ReminderCard(initial, false) { saved = it } } }
        compose.onNodeWithText("03.11.2026").performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithText("03.11.2026").performClick()
        compose.onNodeWithText("Выбрать").performClick()
        compose.runOnIdle { assertEquals(initial.anchor, saved!!.anchor) }
    }
    @Test fun glucoseCycleUsesSamePauseSwitchAndIntervalDialog() {
        var toggled = 0
        var saved: GlucoseCycle? = null
        val initial = GlucoseCycle(startDate = "2026-11-03")
        compose.setContent { MaterialTheme { CycleSettings(initial, false, { toggled++ }, { saved = it }) } }
        compose.onNodeWithContentDescription("Напоминания: Глюкоза").performClick()
        compose.runOnIdle { assertEquals(1, toggled) }
        compose.onNodeWithText("Через 120 мин после еды").performClick()
        compose.onNodeWithContentDescription("Интервал после еды").performTextReplacement("90")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle { assertEquals(90, saved!!.afterMinutes) }
        compose.onNodeWithText("Цикл контроля глюкозы").assertDoesNotExist()
    }
}
