package dev.dadyarri.medtracker.reminders

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReminderV6MigrationTest {
    @Test fun existingScheduledReminderSurvivesNewMetadataColumns() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "reminder-v6-migration.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val schema = JSONObject(File("schemas/dev.dadyarri.medtracker.alarm.AlarmDatabase/6.json").readText())
                .getJSONObject("database").getJSONArray("entities")
            for (i in 0 until schema.length()) {
                val entity = schema.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO reminder_occurrence VALUES ('pressure/date', 'PRESSURE', '480', 'Давление', 1000, 1000, 7201000, 'WAITING', '', 0, NULL)")
            old.version = 6
        }
        val db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
        try {
            val row = db.reminders().all().single()
            assertEquals("pressure/date", row.key)
            assertEquals(1000L, row.nextAt)
            assertEquals("WAITING", row.status)
            assertNull(row.food); assertNull(row.meal); assertNull(row.mealEventId); assertNull(row.recordId)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
