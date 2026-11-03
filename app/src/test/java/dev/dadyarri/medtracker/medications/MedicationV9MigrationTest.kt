package dev.dadyarri.medtracker.medications

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import kotlinx.coroutines.flow.first
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
class MedicationV9MigrationTest {
    @Test fun removingFormPreservesMedicationRegimensAndIntakes() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "medications-v9.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = JSONObject(File("schemas/dev.dadyarri.medtracker.alarm.AlarmDatabase/9.json").readText())
                .getJSONObject("database").getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length())
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO medication VALUES ('m', 'Препарат', 'Капсулы', '10 мг', 'капс.', '0.5', 'После еды', 'Заметка', 'CAPSULE', 0, 4290306047, 4294957782, 0, 1000, 2000)")
            old.execSQL("INSERT INTO medication_regimen VALUES ('r', 'm', 'COURSE', 1000, 9000, NULL, 1000)")
            old.execSQL("INSERT INTO regimen_version VALUES ('v', 'r', 1000, '0.5', 'капс.', 'DAILY', '480', '1,2,3,4,5,6,7', 1, 8, '2026-11-03', NULL, 'AFTER', 0, 'BREAKFAST', 30)")
            old.execSQL("INSERT INTO medication_intake VALUES ('i', 'm', 'r', 'v', '0.5', 'капс.', 3000, 4000, 5000, 'Комментарий')")
            old.execSQL("INSERT INTO blood_pressure VALUES ('p', 120, 80, 70, 'MORNING', '', 1000, 1000, 1000)")
            old.version = 9
        }
        val db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
        try {
            val medication = db.medications().medications().first().single()
            assertEquals(Medication("m", "Препарат", strength = "10 мг", unit = "капс.", usualDose = "0.5",
                instruction = "После еды", note = "Заметка", shape = "CAPSULE",
                color = 4290306047, secondColor = 4294957782, createdAt = 1000, updatedAt = 2000), medication)
            assertEquals(MedicationRegimen("r", "m", "COURSE", 1000, endsAt = 9000, createdAt = 1000),
                db.medications().regimens().first().single())
            assertEquals(RegimenVersion("v", "r", 1000, "0.5", "капс.", "DAILY",
                anchorDate = "2026-11-03", food = "AFTER"), db.medications().versions().first().single())
            assertEquals(MedicationIntake("i", "m", "r", "v", "0.5", "капс.", 3000, 4000, 5000, "Комментарий"),
                db.medications().intakes().first().single())
            assertEquals("p", db.pressure().observe().first().single().id)
            val sql = db.openHelper.writableDatabase
            sql.query("PRAGMA table_info(medication)").use { cursor ->
                val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                assertFalse("form" in columns)
            }
            sql.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            MedicationService(db).save(medication.copy(name = "Изменённый препарат"))
            assertEquals("Изменённый препарат", db.medications().medication("m")!!.name)
            assertEquals("i", db.medications().intakes().first().single().id)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
