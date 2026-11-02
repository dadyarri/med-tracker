package dev.dadyarri.medtracker.reminders

import android.app.*
import android.database.sqlite.SQLiteDatabase
import android.media.RingtoneManager
import androidx.core.net.toUri
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, shadows = [FullScreenNotificationShadow::class])
class ReminderDeliveryTest {
    @Test fun notificationUsesAlarmCategoryGroupedTitlesAndFullscreenOnlyWithAccess() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(NotificationManager::class.java)
        val shadow = org.robolectric.shadow.api.Shadow.extract<FullScreenNotificationShadow>(manager)
        val now = System.currentTimeMillis()
        val rows = listOf("Давление", "Завтрак").mapIndexed { index, title ->
            ReminderOccurrence("$index", "$index", "r", title, now, now + 900_000, now + 7_200_000, "RINGING", "group")
        }
        val notifications = ReminderNotifications(context)
        shadow.fullscreenAllowed = true
        val fullscreen = notifications.build(rows, true)
        assertEquals(Notification.CATEGORY_ALARM, fullscreen.category)
        assertNotNull(fullscreen.fullScreenIntent)
        assertTrue(fullscreen.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Давление"))
        assertTrue(fullscreen.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("Завтрак"))
        assertNull(notifications.build(rows, false).fullScreenIntent)
        shadow.fullscreenAllowed = false
        assertNull(notifications.build(rows, true).fullScreenIntent)
        assertNull(notifications.build(rows.map { it.copy(status = "MUTED") }, true).fullScreenIntent)
    }
    @Test fun appToneRoundTripDoesNotChangeSystemDefault() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = AlarmToneStore(context)
        val initial = store.current()
        val system = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        try {
            val selected = "content://media/internal/audio/media/123".toUri()
            store.save(selected)
            assertEquals(selected, AlarmToneStore(context).current())
            assertEquals(system, RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM))
        } finally { store.save(initial) }
    }
    @Test fun migrationFromVersionFiveRetainsMedicalDataAndCreatesOccurrences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "reminder-v5-migration.db"
        context.deleteDatabase(name)
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        val schema = JSONObject(File("schemas/dev.dadyarri.medtracker.alarm.AlarmDatabase/5.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (i in 0 until indices.length())
                    old.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            old.execSQL("INSERT INTO blood_pressure VALUES ('p', 120, 80, 70, 'MORNING', '', 100, 200, 200)")
            old.execSQL("INSERT INTO meal_event VALUES ('m', 'BREAKFAST', 100, 200, 200)")
            old.execSQL("INSERT INTO glucose_cycle VALUES (1, 1, '2026-11-03', 480, 780, 1140, 120)")
            old.version = 5
        }
        val db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).build()
        try {
            assertEquals(120, db.pressure().get("p")!!.systolic)
            assertEquals("BREAKFAST", db.meals().get("m")!!.type)
            assertEquals(true, db.meals().cycle().first()!!.active)
            assertTrue(db.reminders().all().isEmpty())
            val row = ReminderOccurrence("p/2026-11-03", "PRESSURE", "480", "Давление", 1000, 1000, 2000)
            db.reminders().save(listOf(row))
            assertEquals(row, db.reminders().all().single())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}

@org.robolectric.annotation.Implements(NotificationManager::class)
class FullScreenNotificationShadow : org.robolectric.shadows.ShadowNotificationManager() {
    var fullscreenAllowed = false
    @org.robolectric.annotation.Implementation(minSdk = 34)
    protected fun canUseFullScreenIntent(): Boolean = fullscreenAllowed
}
