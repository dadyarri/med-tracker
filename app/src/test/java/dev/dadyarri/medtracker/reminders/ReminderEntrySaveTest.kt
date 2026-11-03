package dev.dadyarri.medtracker.reminders

import android.app.Application
import android.app.AlarmManager
import android.os.Looper
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.meals.MealEvent
import dev.dadyarri.medtracker.pressure.PressureDraft
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReminderEntrySaveTest {
    private lateinit var db: AlarmDatabase
    private lateinit var controller: ReminderAlarmController
    private lateinit var model: ReminderEntryViewModel
    private lateinit var store: ReminderSettingsStore
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java).allowMainThreadQueries().build()
        store = ReminderSettingsStore(context)
        runBlocking { store.settings.first().forEach { store.save(it.copy(paused = true)) } }
        controller = ReminderAlarmController(context, db, store)
        model = ReminderEntryViewModel(db, controller)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }
    @After fun cleanup() { controller.scope.cancel(); db.close() }
    private suspend fun seed(source: String): ReminderOccurrence {
        val now = System.currentTimeMillis()
        val row = ReminderOccurrence("$source/date", source, "r", source, now, now + 900_000, now + 7_200_000,
            "RINGING", "group", food = if (source == "GLUCOSE") "AFTER" else null,
            meal = if (source == "GLUCOSE") "BREAKFAST" else null)
        db.reminders().save(listOf(row))
        return row
    }
    @Test fun everySupportedFormCommitsRecordAndCompletionTogetherAndPreventsDuplicate() = runBlocking {
        for (source in listOf("PRESSURE", "WEIGHT", "GLUCOSE", "BREAKFAST")) {
            val row = seed(source)
            val now = System.currentTimeMillis()
            var done = 0
            when (source) {
                "PRESSURE" -> {
                    val draft = PressureDraft("120", "80", "70", PressureDraft.format(now), originalAt = now)
                    model.savePressure(row, draft) { done++ }
                    model.savePressure(row, draft) { done++ }
                }
                "BREAKFAST" -> model.saveMeal(row, MealEvent("ignored", "BREAKFAST", now, now, now)) { done++ }
                else -> model.saveMeasurement(row, row.measurementDraft(now).copy(value = if (source == "WEIGHT") "70,1" else "5,7")) { done++ }
            }
            await { done == 1 && !model.busy.value }
            val saved = db.reminders().all().single { it.key == row.key }
            assertEquals("COMPLETED", saved.status)
            assertNull(saved.nextAt)
            assertEquals(row.entryId, saved.recordId)
            when (source) {
                "PRESSURE" -> assertEquals(now, db.pressure().get(row.entryId)!!.measuredAt)
                "WEIGHT" -> assertEquals(701, db.measurements().weight(row.entryId)!!.tenthsKg)
                "GLUCOSE" -> {
                    val glucose = db.measurements().glucose(row.entryId)!!
                    assertEquals(570, glucose.hundredths)
                    assertEquals("AFTER", glucose.foodContext)
                    assertEquals("BREAKFAST", glucose.meal)
                }
                else -> assertEquals("BREAKFAST", db.meals().get(row.entryId)!!.type)
            }
        }
    }
    @Test fun staleRevisionWritesNothingAndCompletionFailureRollsBackRecord() = runBlocking {
        val row = seed("PRESSURE")
        val draft = PressureDraft("120", "80", "", PressureDraft.format(System.currentTimeMillis()))
        model.savePressure(row.copy(revision = "stale"), draft) { fail("Must not save stale task") }
        await { !model.busy.value }
        assertNotNull(model.error.value)
        assertTrue(db.pressure().observe().first().isEmpty())
        try {
            controller.saveEntry(row.key, row.revision) {
                db.meals().insert(MealEvent("rollback", "BREAKFAST", row.dueAt, row.dueAt, row.dueAt))
                error("Simulated failure")
            }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertNull(db.meals().get("rollback"))
        assertEquals("RINGING", db.reminders().all().single().status)
    }
    @Test fun completingOneTaskKeepsOtherAndFutureDaysActive() = runBlocking {
        val minute = LocalTime.now().plusMinutes(30).let { it.hour * 60 + it.minute }
        store.save(ReminderSetting(ReminderKind.PRESSURE, false, minute))
        store.save(ReminderSetting(ReminderKind.BREAKFAST, false, minute))
        controller.reconcile()
        val original = db.reminders().all()
        val pressure = original.first { it.source == "PRESSURE" && it.active }
        val breakfast = original.first { it.source == "BREAKFAST" && it.active }
        db.reminders().save(listOf(pressure.copy(status = "RINGING", token = "g"),
            breakfast.copy(status = "RINGING", token = "g")))
        var done = false
        model.savePressure(pressure, PressureDraft("120", "80", "", PressureDraft.format(System.currentTimeMillis()))) { done = true }
        await { done && !model.busy.value }
        assertEquals("RINGING", db.reminders().all().single { it.key == breakfast.key }.status)
        assertTrue(db.reminders().all().any { it.source == "PRESSURE" && it.active })
        val manager = RuntimeEnvironment.getApplication().getSystemService(AlarmManager::class.java)
        assertNotNull(manager.nextAlarmClock)
    }
    private fun await(condition: () -> Boolean) {
        repeat(400) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Operation did not complete", condition())
    }
}
