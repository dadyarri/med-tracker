package dev.dadyarri.medtracker.medications

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
class MedicationV8MigrationTest {
    @Test fun addingMedicationTablesPreservesEveryExistingRecordType() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "medications-v8.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = JSONObject(File("schemas/dev.dadyarri.medtracker.alarm.AlarmDatabase/8.json").readText())
                .getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length())
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO blood_pressure VALUES ('p', 120, 80, 70, 'MORNING', '', 1000, 1000, 1000)")
            old.execSQL("INSERT INTO weight VALUES ('w', 700, 'MORNING', '', 1000, 1000, 1000)")
            old.execSQL("INSERT INTO meal_event VALUES ('meal', 'BREAKFAST', 1000, 1000, 1000)")
            old.execSQL("INSERT INTO glucose VALUES ('g', 570, 'MMOL', 'AFTER', NULL, '', 5401000, 5401000, 5401000, 'BREAKFAST', 'meal')")
            old.execSQL("""INSERT INTO glucose_cycle (id, active, startDate, breakfastMinute, lunchMinute, dinnerMinute, afterMinutes)
                VALUES (1, 1, '2026-11-03', 510, 780, 1140, 90)""")
            old.version = 8
        }
        val db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
        try {
            assertEquals("p", db.pressure().observe().first().single().id)
            assertEquals(700, db.measurements().weights().first().single().tenthsKg)
            assertEquals("meal", db.measurements().glucose().first().single().mealEventId)
            assertEquals("meal", db.meals().observe().first().single().id)
            assertEquals(90, db.meals().cycle().first()!!.afterMinutes)
            assertTrue(db.medications().medications().first().isEmpty())
            MedicationService(db).save(Medication("m", "Препарат"))
            assertEquals("m", db.medications().medications().first().single().id)
            assertEquals("p", db.pressure().observe().first().single().id)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
