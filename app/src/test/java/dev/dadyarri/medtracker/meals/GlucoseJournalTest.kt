package dev.dadyarri.medtracker.meals

import android.app.*
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.measurements.GlucoseRecord
import dev.dadyarri.medtracker.reminders.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class GlucoseJournalTest {
    private lateinit var db: AlarmDatabase
    private lateinit var controller: ReminderAlarmController
    private lateinit var row: ReminderOccurrence
    @Before fun setup() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java).allowMainThreadQueries().build()
        val store = ReminderSettingsStore(context)
        store.settings.first().forEach { store.save(it.copy(paused = true)) }
        controller = ReminderAlarmController(context, db, store)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val now = System.currentTimeMillis()
        db.meals().saveCycle(GlucoseCycle(active = true, startDate = LocalDate.now().toString()))
        db.meals().insert(MealEvent("meal", "BREAKFAST", now, now, now))
        controller.reconcile()
        row = db.reminders().all().single { it.key.endsWith("-after") }
    }
    @After fun cleanup() { controller.scope.cancel(); db.close() }
    private fun fact() = GlucoseRecord("journal", 570, "MMOL", "AFTER", null, "", row.dueAt,
        System.currentTimeMillis(), System.currentTimeMillis(), "BREAKFAST", "meal")
    @Test fun journalInsertEditDeleteAndMealCorrectionUpdateRoomAndNativeAlarmTogether() = runBlocking {
        val record = fact()
        controller.changeJournal { db.measurements().insert(record) }
        assertEquals("journal", db.reminders().all().single { it.key == row.key }.recordId)
        controller.changeJournal { db.measurements().update(record.copy(foodContext = "NONE")) }
        assertEquals("WAITING", db.reminders().all().single { it.key == row.key }.status)
        controller.changeJournal { db.measurements().update(record) }
        assertEquals("COMPLETED", db.reminders().all().single { it.key == row.key }.status)
        controller.changeJournal {
            val meal = db.meals().get("meal")!!
            db.meals().update(meal.copy(startedAt = meal.startedAt + 120 * 60_000L))
        }
        val moved = db.reminders().all().single { it.key == row.key }
        assertEquals("WAITING", moved.status)
        assertEquals(row.dueAt + 120 * 60_000L, moved.nextAt)
        assertEquals(record.measuredAt, db.measurements().glucose(record.id)!!.measuredAt)
        val manager = RuntimeEnvironment.getApplication().getSystemService(AlarmManager::class.java)
        assertEquals(1, Shadows.shadowOf(manager).scheduledAlarms.size)
        controller.changeJournal { db.measurements().deleteGlucose(record.id) }
        assertNull(db.reminders().all().single { it.key == row.key }.recordId)
    }
    @Test fun journalFailureRollsBackFactAndTask() = runBlocking {
        val before = db.reminders().all()
        try {
            controller.changeJournal { db.measurements().insert(fact()); error("rollback") }
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertNull(db.measurements().glucose("journal"))
        assertEquals(before, db.reminders().all())
    }
    @Test fun externalJournalWritesAreAlsoObservedWithoutOpeningUi() = runBlocking {
        controller.start()
        db.measurements().insert(fact())
        withTimeout(5_000) {
            while (db.reminders().all().single { it.key == row.key }.status != "COMPLETED") delay(10)
        }
        db.measurements().deleteGlucose("journal")
        withTimeout(5_000) {
            while (db.reminders().all().single { it.key == row.key }.status != "WAITING") delay(10)
        }
    }
}
