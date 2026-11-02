package dev.dadyarri.medtracker.measurements

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.pressure.PressureDraft
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MeasurementDatabaseTest {
    @Test fun versionTwoMigrationRetainsPressureAlarmAndNewRecordsAfterReopen() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "measurement-migration.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE test_alarm (id INTEGER NOT NULL, token TEXT NOT NULL, nextAt INTEGER, endAt INTEGER NOT NULL, sound INTEGER NOT NULL, status TEXT NOT NULL, snoozes INTEGER NOT NULL, repeatMinutes INTEGER NOT NULL, snoozeMinutes INTEGER NOT NULL, diagnostic TEXT, PRIMARY KEY(id))")
            old.execSQL("CREATE TABLE alarm_event (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, at INTEGER NOT NULL, description TEXT NOT NULL)")
            old.execSQL("CREATE TABLE blood_pressure (id TEXT NOT NULL PRIMARY KEY, systolic INTEGER NOT NULL, diastolic INTEGER NOT NULL, pulse INTEGER, context TEXT, comment TEXT NOT NULL, measuredAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
            old.execSQL("CREATE INDEX index_blood_pressure_measuredAt ON blood_pressure (measuredAt)")
            old.execSQL("INSERT INTO test_alarm VALUES (1, 'keep-alarm', 1000, 2000, 0, 'WAITING', 0, 15, 5, NULL)")
            old.execSQL("INSERT INTO alarm_event VALUES (1, 500, 'event')")
            old.execSQL("INSERT INTO blood_pressure VALUES ('p', 120, 80, 70, 'MORNING', 'kept', 500, 600, 700)")
            old.version = 2
        }
        var db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
        try {
            assertEquals("kept", db.pressure().get("p")!!.comment)
            assertEquals("keep-alarm", db.alarms().current()!!.token)
            assertEquals("event", db.alarms().events().first().single().description)
            val weight = WeightRecord("w", 723, null, "", 100, 200, 200)
            val glucose = GlucoseRecord("g", 567, "MMOL", "FASTING", "UNPLANNED", "", 90, 200, 200)
            db.measurements().insert(weight)
            assertEquals(-1L, db.measurements().insert(weight.copy(tenthsKg = 999)))
            db.measurements().insert(glucose)
            db.close()
            db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
            assertEquals(weight, db.measurements().weights().first().single())
            assertEquals(glucose, db.measurements().glucose().first().single())
            assertEquals(1, db.measurements().update(glucose.copy(hundredths = 600)))
            assertEquals(600, db.measurements().glucose("g")!!.hundredths)
            assertEquals(1, db.measurements().deleteWeight("w"))
            assertNull(db.measurements().weight("w"))
            assertEquals(1, db.measurements().deleteGlucose("g"))
            assertNull(db.measurements().glucose("g"))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun viewModelPreventsDuplicateSaveAndPreservesTimestampsOnEdit() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlarmDatabase::class.java).build()
        val vm = MeasurementViewModel(db.measurements())
        try {
            for (type in MeasurementType.entries) {
                val id = type.name
                val draft = MeasurementDraft(type, if (type == MeasurementType.WEIGHT) "72,3" else "5,67", "03.11.2026 08:45")
                var completed = 0
                vm.save(id, false, draft) { completed++ }
                vm.save(id, false, draft) { completed++ }
                await { completed > 0 && !vm.busy.value }
                val at = draft.instant()!! + 12_345
                val created = if (type == MeasurementType.WEIGHT) {
                    val old = db.measurements().weight(id)!!
                    db.measurements().update(old.copy(measuredAt = at))
                    old.createdAt
                } else {
                    val old = db.measurements().glucose(id)!!
                    db.measurements().update(old.copy(measuredAt = at))
                    old.createdAt
                }
                vm.save(id, true, draft.copy(time = PressureDraft.format(at), comment = "edited")) { completed++ }
                await { !vm.busy.value }
                if (type == MeasurementType.WEIGHT) {
                    val saved = db.measurements().weight(id)!!
                    assertEquals(at, saved.measuredAt); assertEquals(created, saved.createdAt)
                    assertEquals(723, saved.tenthsKg); assertEquals("edited", saved.comment)
                } else {
                    val saved = db.measurements().glucose(id)!!
                    assertEquals(at, saved.measuredAt); assertEquals(created, saved.createdAt)
                    assertEquals(567, saved.hundredths); assertEquals("edited", saved.comment)
                }
            }
        } finally { vm.viewModelScope.cancel(); db.close() }
    }
    private fun await(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Expected operation to finish", condition())
    }
}
