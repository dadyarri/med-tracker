package dev.dadyarri.medtracker.reminders

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.*
import dev.dadyarri.medtracker.meals.MealEvent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReminderEntryUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun plannedCardAndAlarmPrimaryActionOpenCorrectRecordWhileMuteStaysSecondary() {
        val now = System.currentTimeMillis()
        val row = ReminderOccurrence("pressure", "PRESSURE", "r", "Давление", now, now, now + 7_200_000, "RINGING", "g")
        var opened: ReminderOccurrence? = null
        var action: String? = null
        compose.setContent { MaterialTheme { ReminderAlarmScreen(listOf(row), false, { opened = it }, { a, _ -> action = a }) } }
        compose.onNodeWithText("Записать давление").performClick()
        compose.runOnIdle { assertEquals(row, opened); assertNull(action) }
        compose.onNodeWithText("Без звука").performClick()
        compose.runOnIdle { assertEquals(AlarmController.MUTE, action) }
        compose.onNodeWithText("Открыть журнал").assertDoesNotExist()
    }
    @Test fun glucoseFormPrefillsFoodAndMealAndSavesWithoutOpeningMainActivity() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java).allowMainThreadQueries().build()
        val store = ReminderSettingsStore(context)
        store.settings.first().forEach { store.save(it.copy(paused = true)) }
        val controller = ReminderAlarmController(context, db, store)
        val model = ReminderEntryViewModel(db, controller)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val now = System.currentTimeMillis()
        val meal = MealEvent("meal", "BREAKFAST", now - 7_200_000, now, now)
        db.meals().insert(meal)
        val row = ReminderOccurrence("glucose", "GLUCOSE", "r", "Глюкоза · после еды", now, now, now + 7_200_000,
            food = "AFTER", meal = "BREAKFAST", mealEventId = "meal")
        db.reminders().save(listOf(row))
        var closed = 0
        try {
            compose.setContent { MaterialTheme { ReminderEntrySheet(ReminderEntryRequest(row.key, row.revision, null),
                listOf(row), { closed++ }, model) } }
            compose.onNodeWithContentDescription("После еды").assertIsOn()
            compose.onNodeWithText("Завтрак").assertExists()
            compose.onNodeWithContentDescription("Глюкоза").performTextInput("5,7")
            compose.onNodeWithText("Сохранить").performScrollTo().performClick()
            compose.waitUntil(5_000) {
                Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                (closed == 1 && !model.busy.value) || model.error.value != null
            }
            assertNull(model.error.value)
            assertEquals(1, closed)
            val record = db.measurements().glucose().first().single()
            assertEquals(570, record.hundredths)
            assertEquals("AFTER", record.foodContext)
            assertEquals("BREAKFAST", record.meal)
            assertEquals("meal", record.mealEventId)
            assertEquals("COMPLETED", db.reminders().all().single().status)
            assertEquals(record.id, db.reminders().all().single().recordId)
        } finally { controller.scope.cancel(); db.close() }
    }
    @Test fun plannedCardHasDistinctLabelAndOpensOnTap() {
        val now = System.currentTimeMillis()
        val row = ReminderOccurrence("weight", "WEIGHT", "r", "Вес", now, now, now + 7_200_000)
        var opened = 0
        compose.setContent { MaterialTheme { PlannedReminderCard(row) { opened++ } } }
        compose.onNodeWithText("Запланировано").assertIsDisplayed()
        compose.onNodeWithText("Вес").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }
}
