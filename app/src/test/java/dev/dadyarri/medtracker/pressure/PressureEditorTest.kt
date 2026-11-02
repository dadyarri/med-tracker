package dev.dadyarri.medtracker.pressure

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PressureEditorTest {
    @get:Rule val compose = createComposeRule()
    private val empty = listOf("", "", "", "03.11.2026 08:45", "", "")

    @Test fun draftSurvivesRecreationAndOptionalFieldsDoNotBlockSaving() {
        var saved: PressureDraft? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme { PressureEditor("new", empty, false, false, null, { saved = it }, {}, {}) }
        }
        compose.onNodeWithText("Верхнее, мм рт. ст.").performTextInput("120")
        compose.onNodeWithText("Нижнее, мм рт. ст.").performTextInput("80")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val record = requireNotNull(saved)
            assertEquals("120", record.systolic)
            assertEquals("80", record.diastolic)
            assertEquals("", record.pulse)
            assertEquals(PressureContext.MORNING.name, record.context)
        }
    }

    @Test fun invalidValuesStayInFormAndUnusualValuesRequireConfirmation() {
        var saves = 0
        compose.setContent {
            MaterialTheme { PressureEditor("new", listOf("300", "80", "", empty[3], "", ""), false, false, null, { saves++ }, {}, {}) }
        }
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.onNodeWithText("Проверьте значения").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saves) }
        compose.onNodeWithText("Сохранить как есть").performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun twoDigitPressureAdvancesToPulseAndThenTime() {
        compose.setContent { MaterialTheme { PressureEditor("new", empty, false, false, null, {}, {}, {}) } }
        val sys = compose.onNodeWithText("Верхнее, мм рт. ст.")
        val dia = compose.onNodeWithText("Нижнее, мм рт. ст.")
        val pulse = compose.onNodeWithText("Пульс, уд/мин · необязательно")
        sys.performClick().performTextInput("7")
        sys.assertIsFocused()
        sys.performTextInput("5")
        dia.assertIsFocused()
        dia.performTextInput("8")
        dia.assertIsFocused()
        dia.performTextInput("0")
        pulse.assertIsFocused()
        pulse.performTextInput("7")
        pulse.assertIsFocused()
        pulse.performTextInput("2")
        compose.onNodeWithContentDescription("Время измерения").assertIsFocused()
    }

    @Test fun threeDigitPressureWaitsForThirdDigitAndDeletionDoesNotAdvance() {
        compose.setContent { MaterialTheme { PressureEditor("new", empty, false, false, null, {}, {}, {}) } }
        val sys = compose.onNodeWithText("Верхнее, мм рт. ст.")
        val dia = compose.onNodeWithText("Нижнее, мм рт. ст.")
        sys.performClick().performTextInput("12")
        sys.assertIsFocused()
        sys.performTextInput("0")
        dia.assertIsFocused()
        dia.performTextInput("10")
        dia.assertIsFocused()
        dia.performTextInput("0")
        compose.onNodeWithText("Пульс, уд/мин · необязательно").assertIsFocused()
        sys.performClick().performTextReplacement("12")
        sys.assertIsFocused()
        sys.performTextClearance()
        sys.assertIsFocused()
    }

    @Test fun changingDateOrTimeRestoresAutomaticContext() {
        var saved: PressureDraft? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme { PressureEditor("new", empty, false, false, null, { saved = it }, {}, {}) }
        }
        compose.onNodeWithText("Утро").assertIsSelected()
        compose.onNodeWithContentDescription("Время измерения").performScrollTo().performClick()
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.TimePickerDialog
            dialog.updateTime(20, 15)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("Вечер").assertIsSelected()
        compose.onNodeWithText("Ночь").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Время измерения").performScrollTo().performClick()
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.TimePickerDialog
            dialog.updateTime(12, 0)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("День").assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("День").assertIsSelected()
        compose.onNodeWithText("Ночь").performScrollTo().performClick()
        compose.onNodeWithText("Дата: 03.11.2026").performScrollTo().performClick()
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.DatePickerDialog
            dialog.updateDate(2026, 1, 28)
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("Дата: 28.02.2026").assertExists()
        compose.onNodeWithText("День").assertIsSelected()
        compose.onNodeWithText("Верхнее, мм рт. ст.").performScrollTo().performTextInput("120")
        compose.onNodeWithText("Нижнее, мм рт. ст.").performTextInput("80")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved)
            assertEquals("28.02.2026 12:00", result.time)
            assertEquals(PressureContext.DAY.name, result.context)
        }
    }

    @Test fun existingContextIsNotReplacedWhenOpeningEditor() {
        compose.setContent {
            MaterialTheme { PressureEditor("edit", empty.toMutableList().apply { this[4] = "NIGHT" }, true, false, null, {}, {}, {}) }
        }
        compose.onNodeWithText("Ночь").assertIsSelected()
        compose.onNodeWithText("Автоматически по времени").assertDoesNotExist()
        compose.onNodeWithContentDescription("Время измерения").performScrollTo().performClick()
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.TimePickerDialog
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
        }
        compose.onNodeWithText("Ночь").assertIsSelected()
        compose.onNodeWithContentDescription("Время измерения").performScrollTo().performClick()
        compose.runOnIdle {
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as android.app.TimePickerDialog
            dialog.updateTime(16, 0)
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
        }
        compose.onNodeWithText("Ночь").assertIsSelected()
    }

    @Test fun deletionNeedsExplicitConfirmation() {
        var deletes = 0
        compose.setContent {
            MaterialTheme { PressureEditor("edit", listOf("120", "80", "", empty[3], "", ""), true, false, null, {}, { deletes++ }, {}) }
        }
        compose.onNodeWithText("Удалить запись").performScrollTo().performClick()
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertEquals(0, deletes) }
        compose.onNodeWithText("Удалить запись").performScrollTo().performClick()
        compose.onNodeWithText("Удалить", substring = false).performClick()
        compose.runOnIdle { assertEquals(1, deletes) }
    }
}
