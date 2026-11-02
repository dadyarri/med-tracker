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
    @org.junit.Before fun use24HourClock() {
        android.provider.Settings.System.putString(org.robolectric.RuntimeEnvironment.getApplication().contentResolver,
            android.provider.Settings.System.TIME_12_24, "24")
    }
    private val empty = listOf("", "", "", "03.11.2026 08:45", "", "")

    @Test fun draftSurvivesRecreationAndOptionalFieldsDoNotBlockSaving() {
        var saved: PressureDraft? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme { PressureEditor("new", empty, false, false, null, { saved = it }, {}, {}) }
        }
        compose.onNodeWithContentDescription("Верхнее").performTextInput("120")
        compose.onNodeWithContentDescription("Нижнее").performTextInput("80")
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

    @Test fun hiddenHistoricalCommentSurvivesEditing() {
        var saved: PressureDraft? = null
        compose.setContent {
            MaterialTheme {
                PressureEditor("edit", listOf("120", "80", "", empty[3], "", "Старая заметка"),
                    true, false, null, { saved = it }, {}, {})
            }
        }
        compose.onNodeWithText("Комментарий").assertDoesNotExist()
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Старая заметка", requireNotNull(saved).comment) }
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
        val sys = compose.onNodeWithContentDescription("Верхнее")
        val dia = compose.onNodeWithContentDescription("Нижнее")
        val pulse = compose.onNodeWithContentDescription("Пульс")
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
        val sys = compose.onNodeWithContentDescription("Верхнее")
        val dia = compose.onNodeWithContentDescription("Нижнее")
        sys.performClick().performTextInput("12")
        sys.assertIsFocused()
        sys.performTextInput("0")
        dia.assertIsFocused()
        dia.performTextInput("10")
        dia.assertIsFocused()
        dia.performTextInput("0")
        compose.onNodeWithContentDescription("Пульс").assertIsFocused()
        sys.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.RequestFocus) { it() }
        sys.performTextReplacement("12")
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
        compose.onNodeWithContentDescription("Утро").assertIsOn()
        compose.onNodeWithContentDescription("Время измерения").performScrollTo().performClick()
        setTime(20, 15)
        compose.onNodeWithText("Выбрать").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("20:15").assertExists()
        compose.onNodeWithContentDescription("Вечер").assertIsOn()
        compose.onNodeWithContentDescription("Ночь").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Время измерения").performScrollTo().performClick()
        setTime(12, 0)
        compose.onNodeWithText("Выбрать").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("День").assertIsOn()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("День").assertIsOn()
        compose.onNodeWithContentDescription("Ночь").performScrollTo().performClick()
        compose.onNodeWithText("03.11.2026").performScrollTo().performClick()
        compose.onNode(hasContentDescription("Switch to text input mode", substring = true)).performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("02282026")
        compose.onNodeWithText("Выбрать").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("28.02.2026").assertExists()
        compose.onNodeWithContentDescription("День").assertIsOn()
        compose.onNodeWithContentDescription("Верхнее").performScrollTo().performTextInput("120")
        compose.onNodeWithContentDescription("Нижнее").performTextInput("80")
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
        compose.onNodeWithContentDescription("Ночь").assertIsOn()
        compose.onNodeWithText("Автоматически по времени").assertDoesNotExist()
        compose.onNodeWithContentDescription("Время измерения").performScrollTo().performClick()
        compose.onNodeWithText("Выбрать").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("Ночь").assertIsOn()
        compose.onNodeWithContentDescription("Время измерения").performScrollTo().performClick()
        setTime(16, 0)
        compose.onNodeWithText("Отмена").performClick()
        compose.onNodeWithContentDescription("Ночь").assertIsOn()
    }

    private fun setTime(hour: Int, minute: Int) {
        compose.onNodeWithText("Ввести время").performScrollTo().performClick()
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).onFirst().performTextReplacement(hour.toString())
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).onLast().performTextReplacement(minute.toString().padStart(2, '0'))
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).onFirst().assertTextEquals(hour.toString().padStart(2, '0'))
        compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(isDialog())).onLast().assertTextEquals(minute.toString().padStart(2, '0'))
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
