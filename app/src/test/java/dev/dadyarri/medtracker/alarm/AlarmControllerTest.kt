package dev.dadyarri.medtracker.alarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class AlarmControllerTest {
    private lateinit var context: Context
    private lateinit var db: AlarmDatabase
    private lateinit var controller: AlarmController
    private lateinit var manager: AlarmManager

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java).allowMainThreadQueries().build()
        controller = AlarmController(context, db)
        manager = context.getSystemService(AlarmManager::class.java)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }
    @After fun tearDown() { controller.scope.cancel(); db.close() }

    @Test fun replacementAndReconciliationKeepExactlyOneSystemAlarm() = runBlocking {
        controller.create(30, false, 15, 120, 5)
        val oldToken = db.alarms().current()!!.token
        controller.create(60, false, 15, 120, 5)
        val current = db.alarms().current()!!
        assertNotEquals(oldToken, current.token)
        repeat(3) { controller.reconcile() }
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        assertEquals(current.nextAt, manager.nextAlarmClock.triggerTime)
        assertEquals(current.token, db.alarms().current()!!.token)
    }

    @Test fun missingExactPermissionPersistsIntentAndRecoversAfterGrant() = runBlocking {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        controller.create(60, false, 15, 120, 5)
        assertNotNull(db.alarms().current()!!.diagnostic)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        controller.reconcile()
        assertNull(db.alarms().current()!!.diagnostic)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
    }

    @Test fun receiverPathRejectsDuplicatesAndStaleActions() = runBlocking {
        controller.create(30, false, 15, 120, 5)
        val initial = db.alarms().current()!!
        db.alarms().save(initial.copy(nextAt = System.currentTimeMillis() - 1))
        controller.deliver(initial.token)
        val delivered = db.alarms().current()!!
        assertEquals(AlarmStatus.RINGING.name, delivered.status)
        val eventCount = db.alarms().events().first().size
        controller.deliver(initial.token)
        assertEquals(eventCount, db.alarms().events().first().size)
        controller.action(AlarmController.SNOOZE, delivered.token)
        val snoozed = db.alarms().current()!!
        assertEquals(AlarmStatus.SNOOZED.name, snoozed.status)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        controller.action(AlarmController.CANCEL, delivered.token)
        assertEquals(snoozed, db.alarms().current())
        controller.action(AlarmController.CANCEL, snoozed.token)
        assertEquals(AlarmStatus.CANCELLED.name, db.alarms().current()!!.status)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }

    @Test fun recoveryNeverLaunchesActivityOrReplaysOverdueAlarm() = runBlocking {
        controller.create(30, true, 15, 120, 5)
        db.alarms().save(db.alarms().current()!!.copy(nextAt = System.currentTimeMillis() - 1))
        controller.reconcile(true, "Перезагрузка")
        assertEquals(AlarmStatus.EXPIRED.name, db.alarms().current()!!.status)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertTrue(db.alarms().events().first().none { it.description == "Сигнал доставлен receiver-у" })
    }

    @Test fun muteKeepsRepeatWithoutConfirmingOrDuplicating() = runBlocking {
        controller.create(30, false, 15, 120, 5)
        val initial = db.alarms().current()!!
        db.alarms().save(initial.copy(nextAt = System.currentTimeMillis() - 1))
        controller.deliver(initial.token)
        val before = db.alarms().current()!!
        controller.action(AlarmController.MUTE, before.token)
        val muted = db.alarms().current()!!
        assertEquals(AlarmStatus.MUTED.name, muted.status)
        assertEquals(before.nextAt, muted.nextAt)
        assertEquals(before.token, muted.token)
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
    }

    @Test fun reopeningDatabaseRetainsFutureTestAndJournal() = runBlocking {
        controller.scope.cancel(); db.close()
        val name = "alarm-persistence-test.db"
        context.deleteDatabase(name)
        db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).allowMainThreadQueries().build()
        controller = AlarmController(context, db)
        controller.create(600, false, 15, 120, 5)
        val saved = db.alarms().current()
        controller.scope.cancel(); db.close()
        db = Room.databaseBuilder(context, AlarmDatabase::class.java, name).allowMainThreadQueries().build()
        controller = AlarmController(context, db)
        controller.reconcile(true, "Обновление APK")
        assertEquals(saved, db.alarms().current())
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        assertEquals(2, db.alarms().events().first().size)
    }
}
