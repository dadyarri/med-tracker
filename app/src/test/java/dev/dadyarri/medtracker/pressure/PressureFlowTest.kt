package dev.dadyarri.medtracker.pressure

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.measurements.MeasurementsHome
import dev.dadyarri.medtracker.measurements.MeasurementViewModel
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PressureFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun todayFiltersAtLocalMidnightAndHistoryHasDateHeadings() {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlarmDatabase::class.java).build()
        val model = PressureViewModel(db.pressure())
        val measurements = MeasurementViewModel(db.measurements())
        val date = java.time.LocalDate.now()
        val start = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dateText = date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu"))
        val previousText = date.minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu"))
        try {
            kotlinx.coroutines.runBlocking {
                db.pressure().insert(PressureRecord("current", 120, 80, null, null, "", start, start, start))
                db.pressure().insert(PressureRecord("previous", 130, 85, null, null, "", start - 1, start, start))
            }
            compose.setContent { MaterialTheme { MeasurementsHome(model, measurements) } }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("Давление: 120 на 80").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Давление: 130 на 85").assertDoesNotExist()
            compose.onNodeWithText(dateText).assertDoesNotExist()
            compose.onNode(hasText("История") and hasClickAction()).performClick()
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)).performScrollToNode(hasText(dateText))
            compose.onNodeWithText(dateText).assertExists()
            val addButton = compose.onNode(hasText("Давление") and !hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            assertTrue(list.fetchSemanticsNode().boundsInRoot.bottom <= addButton.top)
            list.performScrollToNode(hasText(previousText))
            compose.onNodeWithText(previousText).assertExists()
            list.performScrollToNode(hasContentDescription("Давление: 130 на 85"))
            compose.onNodeWithContentDescription("Давление: 130 на 85").assertExists()
            assertEquals(addButton, compose.onNode(hasText("Давление") and !hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))).assertIsDisplayed().fetchSemanticsNode().boundsInRoot)
            compose.onNode(hasText("Настройки") and hasClickAction()).performClick()
            compose.onNode(hasText("Давление") and hasClickAction()).assertDoesNotExist()
        } finally { model.viewModelScope.cancel(); measurements.viewModelScope.cancel(); db.close() }
    }

    @Test fun createInTodayThenDeleteFromHistory() {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlarmDatabase::class.java).build()
        val model = PressureViewModel(db.pressure())
        val measurements = MeasurementViewModel(db.measurements())
        try {
            compose.setContent { MaterialTheme { MeasurementsHome(model, measurements) } }
            compose.onNodeWithText("Давление").performClick()
            compose.onNodeWithContentDescription("Верхнее").performTextInput("120")
            compose.onNodeWithContentDescription("Нижнее").performTextInput("80")
            compose.onNodeWithText("Сохранить").performScrollTo().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithContentDescription("Давление: 120 на 80").fetchSemanticsNodes().size == 1
            }
            compose.onNode(hasText("История") and hasClickAction()).performClick()
            compose.onNodeWithContentDescription("Давление: 120 на 80").performScrollTo().performClick()
            compose.onNodeWithText("Удалить запись").performScrollTo().performClick()
            compose.onNodeWithText("Удалить", substring = false).performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Нет измерений за выбранный период").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            model.viewModelScope.cancel()
            measurements.viewModelScope.cancel()
            db.close()
        }
    }
}
