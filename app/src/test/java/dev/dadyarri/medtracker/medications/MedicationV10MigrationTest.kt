package dev.dadyarri.medtracker.medications

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.reminders.ReminderOccurrence
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MedicationV10MigrationTest {
    @Test fun addingMedicationPayloadPreservesExistingReminderState() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "medications-v10.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = JSONObject(File("schemas/dev.dadyarri.medtracker.alarm.AlarmDatabase/10.json").readText())
                .getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length())
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO reminder_occurrence VALUES ('task', 'GLUCOSE', 'revision', 'Глюкоза', 1000, NULL, 7201000, 'COMPLETED', '', 2, NULL, 'AFTER', 'BREAKFAST', 'meal', 'record', 'signature')")
            old.version = 10
        }
        val db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
        try {
            assertEquals(ReminderOccurrence("task", "GLUCOSE", "revision", "Глюкоза", 1000, null, 7201000,
                status = "COMPLETED", snoozes = 2, food = "AFTER", meal = "BREAKFAST", mealEventId = "meal",
                recordId = "record", completionSignature = "signature"), db.reminders().all().single())
            val sql = db.openHelper.writableDatabase
            assertEquals(11, sql.version)
            sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
