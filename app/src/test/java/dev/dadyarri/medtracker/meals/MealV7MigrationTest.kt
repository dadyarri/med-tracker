package dev.dadyarri.medtracker.meals

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import kotlinx.coroutines.flow.first
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
class MealV7MigrationTest {
    @Test fun existingCycleAndLinkedRecordsSurviveWithDefaultDayConfiguration() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "meals-v7.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = JSONObject(File("schemas/dev.dadyarri.medtracker.alarm.AlarmDatabase/7.json").readText())
                .getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length())
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO glucose_cycle VALUES (1, 1, '2026-11-03', 510, 780, 1140, 90)")
            old.execSQL("INSERT INTO meal_event VALUES ('meal', 'BREAKFAST', 1000, 1000, 1000)")
            old.execSQL("INSERT INTO glucose VALUES ('g', 570, 'MMOL', 'AFTER', NULL, '', 5401000, 5401000, 5401000, 'BREAKFAST', 'meal')")
            old.execSQL("""INSERT INTO reminder_occurrence
                (`key`, source, revision, title, dueAt, nextAt, endAt, status, token, snoozes, recordId)
                VALUES ('GLUCOSE/date-after', 'GLUCOSE', 'r', 'Глюкоза', 5401000, NULL, 12601000, 'COMPLETED', '', 0, 'g')""")
            old.version = 7
        }
        val db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
        try {
            val cycle = db.meals().cycle().first()!!
            assertTrue(cycle.active)
            assertEquals(510, cycle.breakfastMinute)
            assertEquals(90, cycle.afterMinutes)
            assertEquals("BREAKFAST/FASTING/AFTER", cycle.day1)
            assertEquals("LUNCH/BEFORE/AFTER", cycle.day2)
            assertEquals("DINNER/BEFORE/AFTER", cycle.day3)
            assertEquals("5401000/AFTER/BREAKFAST/meal", db.reminders().all().single().completionSignature)
            assertEquals("COMPLETED", db.reminders().all().single().status)
            assertEquals("meal", db.measurements().glucose("g")!!.mealEventId)
            assertEquals(1000L, db.meals().get("meal")!!.startedAt)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
