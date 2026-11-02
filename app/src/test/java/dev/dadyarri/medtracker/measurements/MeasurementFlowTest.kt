package dev.dadyarri.medtracker.measurements

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.pressure.PressureViewModel
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MeasurementFlowTest {
    @get:Rule val compose = createComposeRule()
    private val list get() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
    @Test fun weightAndGlucoseShareHistoryAndFilterChangesChartStatistics() {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlarmDatabase::class.java).build()
        val pressure = PressureViewModel(db.pressure())
        val model = MeasurementViewModel(db.measurements())
        try {
            compose.setContent { MaterialTheme { MeasurementsHome(pressure, model) } }
            compose.onNodeWithText("Вес").performClick()
            compose.onNodeWithContentDescription("Вес").performTextInput("72,3")
            compose.onNodeWithText("Сохранить").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Вес: 72,3").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Глюкоза").performClick()
            compose.onNodeWithContentDescription("Глюкоза").performTextInput("5,5")
            compose.onNodeWithContentDescription("Натощак").performScrollTo().performClick()
            compose.onNodeWithText("Сохранить").performScrollTo().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Глюкоза: 5,5").fetchSemanticsNodes().isNotEmpty() }
            compose.onNode(hasText("История") and hasClickAction()).performClick()
            // Type chips live inside the list; the fixed add buttons do not.
            list.performScrollToIndex(0)
            compose.onNode(hasText("Глюкоза") and isSelectable()).performScrollTo().performClick().assertIsSelected()
            list.performScrollToNode(hasText("Графики и статистика"))
            compose.onNodeWithText("Графики и статистика").performClick()
            list.performScrollToNode(hasText("Измерений: 1"))
            compose.onNodeWithText("Измерений: 1").assertIsDisplayed()
            compose.onAllNodesWithContentDescription("Вес: 72,3").assertCountEquals(0)
            list.performScrollToNode(hasContentDescription("Глюкоза: 5,5"))
            compose.onNodeWithContentDescription("Глюкоза: 5,5").performClick()
            compose.onNodeWithText("Удалить запись").performScrollTo().performClick()
            compose.onNodeWithText("Удалить").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Нет измерений за выбранный период").fetchSemanticsNodes().isNotEmpty() }
            list.performScrollToNode(hasText("Нет измерений за выбранный период"))
            compose.onNodeWithText("Нет измерений за выбранный период").assertIsDisplayed()
        } finally { pressure.viewModelScope.cancel(); model.viewModelScope.cancel(); db.close() }
    }
}
