package dev.dadyarri.medtracker.measurements

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MeasurementEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun glucoseUnitConversionAndFoodTagSurviveRecreation() {
        var saved: MeasurementDraft? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                MeasurementEditor("g", MeasurementDraft(MeasurementType.GLUCOSE, time = "03.11.2026 08:45"),
                    false, false, null, { saved = it }, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Глюкоза").performTextInput("5,5")
        compose.onNodeWithText("мг/дл").assertDoesNotExist()
        compose.onNodeWithContentDescription("Глюкоза").assertTextEquals("5,5")
        compose.onNodeWithContentDescription("После еды").performScrollTo().performClick()
        compose.onNodeWithText("Дополнительно").performScrollTo().performClick()
        compose.onNodeWithText("Внепланово").performScrollTo().performClick()
        compose.onNodeWithText("Приём пищи").performScrollTo().performClick()
        compose.onNodeWithText("Завтрак").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Глюкоза").assertTextEquals("5,5")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle {
            val result = checkNotNull(saved)
            assertEquals(550, result.scaled())
            assertEquals(GlucoseUnit.MMOL, result.unit)
            assertEquals(FoodContext.AFTER, result.food)
            assertEquals(GlucoseTag.UNPLANNED, result.tag)
            assertEquals(MealType.BREAKFAST, result.meal)
        }
    }
    @Test fun weightRejectsExcessPrecisionAndWarnsWithoutBlockingUnusualValue() {
        var saved: MeasurementDraft? = null
        compose.setContent { MaterialTheme {
            MeasurementEditor("w", MeasurementDraft(MeasurementType.WEIGHT, time = "03.11.2026 08:45"),
                false, false, null, { saved = it }, {}, {})
        } }
        compose.onNodeWithContentDescription("Вес").performTextInput("72,34")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.runOnIdle { assertNull(saved) }
        compose.onNodeWithContentDescription("Вес").performTextReplacement("450,1")
        compose.onNodeWithText("Сохранить").performScrollTo().performClick()
        compose.onNodeWithText("Проверьте значение").assertIsDisplayed()
        compose.onAllNodesWithText("Сохранить").filter(hasAnyAncestor(isDialog())).onFirst().performClick()
        compose.runOnIdle { assertEquals(4501, saved!!.scaled()) }
    }
}
