package dev.dadyarri.medtracker.pressure

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
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

    @Test fun createInTodayThenDeleteFromHistory() {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlarmDatabase::class.java).build()
        val model = PressureViewModel(db.pressure())
        try {
            compose.setContent { MaterialTheme { PressureHome(model) } }
            compose.onNodeWithText("Записать давление").performClick()
            compose.onNodeWithText("Верхнее, мм рт. ст.").performTextInput("120")
            compose.onNodeWithText("Нижнее, мм рт. ст.").performTextInput("80")
            compose.onNodeWithText("Сохранить").performScrollTo().performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("120/80 мм рт. ст.").fetchSemanticsNodes().size == 1
            }
            compose.onNode(hasText("История") and hasClickAction()).performClick()
            compose.onNodeWithText("120/80 мм рт. ст.").performClick()
            compose.onNodeWithText("Удалить запись").performScrollTo().performClick()
            compose.onNodeWithText("Удалить", substring = false).performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Измерений давления пока нет").fetchSemanticsNodes().isNotEmpty()
            }
        } finally {
            model.viewModelScope.cancel()
            db.close()
        }
    }
}
