package dev.dadyarri.medtracker

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class SetupNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allPermissionsCanBeSkippedAndStepSurvivesRecreation() {
        var finished = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            MaterialTheme {
                PermissionWizard(
                    granted = List(5) { false }, busy = false, message = null,
                    onRequest = {}, onSamsungConfirmed = {}, onFinish = { finished++ },
                )
            }
        }
        compose.onNodeWithText("Шаг 1 из 5").assertIsDisplayed()
        compose.onNodeWithText("Пропустить").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Шаг 2 из 5").assertIsDisplayed()
        compose.onNodeWithText("Назад").performClick()
        compose.onNodeWithText("Шаг 1 из 5").assertIsDisplayed()
        repeat(4) { compose.onNodeWithText("Пропустить").performClick() }
        compose.onNodeWithText("Шаг 5 из 5").assertIsDisplayed()
        compose.onNodeWithText("Открыть MedTracker").performClick()
        compose.runOnIdle { assertEquals(1, finished) }
    }

    @Test fun navigationOpensEmptyDestinationsAndRestoresSelection() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { EmptyHome() } }
        for (title in listOf("История", "Лекарства", "Настройки", "Сегодня")) {
            compose.onNode(hasText(title) and hasClickAction()).performClick()
            compose.onNode(hasText(title) and isSelected()).assertExists()
        }
        compose.onNode(hasText("Настройки") and hasClickAction()).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasText("Настройки") and isSelected()).assertExists()
        compose.onNodeWithText("Тестовый сигнал", substring = true).assertDoesNotExist()
    }
}
