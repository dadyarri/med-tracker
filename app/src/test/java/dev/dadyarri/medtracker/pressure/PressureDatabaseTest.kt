package dev.dadyarri.medtracker.pressure

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PressureDatabaseTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private fun record(id: String, at: Long) = PressureRecord(id, 120, 80, null, null, "", at, 10, 10)

    @Test fun crudOrdersByMeasurementTimeAndDoesNotDuplicateIds() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java).build()
        try {
            val dao = db.pressure()
            dao.insert(record("a", 200))
            dao.insert(record("b", 100))
            assertEquals(-1L, dao.insert(record("a", 300)))
            assertEquals(listOf("a", "b"), dao.observe().first().map { it.id })
            val edited = record("b", 400).copy(pulse = 65, context = "NIGHT", comment = "Комментарий", updatedAt = 20)
            assertEquals(1, dao.update(edited))
            assertEquals(edited, dao.observe().first().first())
            assertEquals(1, dao.delete("b"))
            assertNull(dao.get("b"))
            assertEquals(1, dao.observe().first().size)
        } finally { db.close() }
    }

    @Test fun versionOneUpgradePreservesAlarmAndJournalAndNewMeasurementsSurviveReopen() = runBlocking {
        val name = "pressure-migration-test.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE IF NOT EXISTS test_alarm (id INTEGER NOT NULL, token TEXT NOT NULL, nextAt INTEGER, endAt INTEGER NOT NULL, sound INTEGER NOT NULL, status TEXT NOT NULL, snoozes INTEGER NOT NULL, repeatMinutes INTEGER NOT NULL, snoozeMinutes INTEGER NOT NULL, diagnostic TEXT, PRIMARY KEY(id))")
            old.execSQL("CREATE TABLE IF NOT EXISTS alarm_event (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, at INTEGER NOT NULL, description TEXT NOT NULL)")
            old.execSQL("INSERT INTO test_alarm VALUES (1, 'retained', 1000, 2000, 0, 'WAITING', 0, 15, 5, NULL)")
            old.execSQL("INSERT INTO alarm_event VALUES (1, 500, 'Событие')")
            old.version = 1
        }
        var db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
        try {
            assertEquals("retained", db.alarms().current()!!.token)
            assertEquals("Событие", db.alarms().events().first().single().description)
            db.pressure().insert(record("saved", 800))
            db.close()
            db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
            assertEquals(record("saved", 800), db.pressure().get("saved"))
            assertEquals("retained", db.alarms().current()!!.token)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
