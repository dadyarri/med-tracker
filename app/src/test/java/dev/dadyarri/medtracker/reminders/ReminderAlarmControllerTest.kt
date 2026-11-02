package dev.dadyarri.medtracker.reminders

import android.app.*
import android.content.Context
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.*
import dev.dadyarri.medtracker.meals.*
import dev.dadyarri.medtracker.measurements.MealType
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReminderAlarmControllerTest {
    private lateinit var context: Context
    private lateinit var db: AlarmDatabase
    private lateinit var store: ReminderSettingsStore
    private lateinit var controller: ReminderAlarmController
    private lateinit var manager: AlarmManager
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java).allowMainThreadQueries().build()
        store = ReminderSettingsStore(context)
        runBlocking { store.settings.first().forEach { store.save(it.copy(paused = true)) } }
        controller = ReminderAlarmController(context, db, store)
        manager = context.getSystemService(AlarmManager::class.java)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }
    @After fun cleanup() { controller.scope.cancel(); db.close() }
    private suspend fun enable(kind: ReminderKind = ReminderKind.PRESSURE) {
        val minute = LocalTime.now().plusMinutes(30).let { it.hour * 60 + it.minute }
        store.save(ReminderSetting(kind, paused = false, minute = minute))
    }
    private suspend fun ring(): List<ReminderOccurrence> {
        controller.reconcile()
        val now = System.currentTimeMillis() - 1
        val rows = db.reminders().all()
        val firstPerSource = rows.filter { it.active }.groupBy { it.source }.values.map { it.minBy { row -> row.dueAt }.key }.toSet()
        db.reminders().save(rows.map { if (it.key in firstPerSource) it.copy(nextAt = now) else it })
        controller.deliver(now)
        return db.reminders().all().filter { it.status == "RINGING" }
    }
    @Test fun enableReplacePauseResumeAndRepeatedReconcileKeepOneAlarm() = runBlocking {
        enable(); repeat(3) { controller.reconcile() }
        assertEquals(1, Shadows.shadowOf(manager).scheduledAlarms.size)
        val old = manager.nextAlarmClock.triggerTime
        val setting = store.settings.first().first { it.kind == ReminderKind.PRESSURE }
        store.save(setting.copy(minute = (setting.minute + 15) % 1440)); controller.reconcile()
        assertNotEquals(old, manager.nextAlarmClock.triggerTime)
        store.save(setting.copy(paused = true)); controller.reconcile()
        assertTrue(Shadows.shadowOf(manager).scheduledAlarms.isEmpty())
        store.save(setting); controller.reconcile()
        assertEquals(1, Shadows.shadowOf(manager).scheduledAlarms.size)
    }
    @Test fun deniedExactPermissionPersistsRowsAndGrantRecovers() = runBlocking {
        enable()
        ShadowAlarmManager.setCanScheduleExactAlarms(false); controller.reconcile()
        assertTrue(db.reminders().all().any { it.active && it.diagnostic != null })
        assertTrue(Shadows.shadowOf(manager).scheduledAlarms.isEmpty())
        ShadowAlarmManager.setCanScheduleExactAlarms(true); controller.reconcile()
        assertTrue(db.reminders().all().all { it.diagnostic == null })
        assertEquals(1, Shadows.shadowOf(manager).scheduledAlarms.size)
    }
    @Test fun coincidentRowsFormOneSessionAndSkippingOneKeepsOther() = runBlocking {
        enable(); enable(ReminderKind.BREAKFAST)
        val ringing = ring()
        assertEquals(2, ringing.size)
        assertEquals(1, ringing.map { it.token }.distinct().size)
        val token = ringing.first().token
        val afterDelivery = db.reminders().all()
        controller.deliver(System.currentTimeMillis() - 1)
        assertEquals(afterDelivery, db.reminders().all())
        controller.action(AlarmController.CANCEL, "stale")
        assertEquals(afterDelivery, db.reminders().all())
        controller.action(AlarmController.CANCEL, token, ringing.first().key)
        assertEquals(1, db.reminders().all().count { it.status == "RINGING" })
        controller.action(AlarmController.MUTE, token)
        assertEquals(1, db.reminders().all().count { it.status == "MUTED" })
    }
    @Test fun snoozeReplacesRepeatAndPauseCancelsActiveSession() = runBlocking {
        enable()
        val row = ring().single()
        val before = System.currentTimeMillis()
        controller.action(AlarmController.SNOOZE, row.token)
        val snoozed = db.reminders().all().single { it.key == row.key }
        assertEquals("SNOOZED", snoozed.status)
        assertEquals(1, snoozed.snoozes)
        assertTrue(snoozed.nextAt!! in (before + 300_000)..(System.currentTimeMillis() + 300_000))
        store.save(store.settings.first().first { it.kind == ReminderKind.PRESSURE }.copy(paused = true))
        controller.reconcile()
        assertTrue(db.reminders().all().none { it.active })
        assertTrue(Shadows.shadowOf(manager).scheduledAlarms.isEmpty())
    }
    @Test fun actualMealSchedulesAfterAndDeletingItCancelsOnlyAfter() = runBlocking {
        val date = LocalDate.now()
        db.meals().saveCycle(GlucoseCycle(active = true, startDate = date.toString()))
        controller.reconcile()
        assertFalse(db.reminders().all().any { it.active && it.key.endsWith("-after") })
        val now = System.currentTimeMillis()
        db.meals().insert(MealEvent("meal", MealType.BREAKFAST.name, now, now, now))
        controller.reconcile()
        assertEquals(now + 120 * 60_000, db.reminders().all().single { it.active && it.key.endsWith("-after") }.dueAt)
        db.meals().delete("meal"); controller.reconcile()
        assertFalse(db.reminders().all().any { it.active && it.key.endsWith("-after") })
    }
    @Test fun observingSettingsRegistersAndCancelsWithoutOpeningAnyScreen() = runBlocking {
        controller.start()
        enable()
        kotlinx.coroutines.withTimeout(5_000) {
            while (Shadows.shadowOf(manager).scheduledAlarms.isEmpty()) kotlinx.coroutines.delay(10)
        }
        val value = store.settings.first().first { it.kind == ReminderKind.PRESSURE }
        store.save(value.copy(paused = true))
        kotlinx.coroutines.withTimeout(5_000) {
            while (Shadows.shadowOf(manager).scheduledAlarms.isNotEmpty()) kotlinx.coroutines.delay(10)
        }
        assertTrue(db.reminders().all().none { it.active })
    }
    @Test fun coldProcessReconciliationDoesNotConsumeFirstFullscreenNotification() = runBlocking {
        enable()
        val row = ring().single()
        val notifications = context.getSystemService(NotificationManager::class.java)
        assertTrue(notifications.activeNotifications.isEmpty())
        controller.reconcile()
        assertTrue(notifications.activeNotifications.isEmpty())
        assertEquals(row.token, db.reminders().all().single { it.key == row.key }.token)
    }
    @Test fun recoveryDoesNotReplayOverdueRows() = runBlocking {
        enable(); controller.reconcile()
        val row = db.reminders().all().first { it.active }
        db.reminders().save(listOf(row.copy(nextAt = System.currentTimeMillis() - 1000)))
        controller.reconcile(true)
        assertEquals("EXPIRED", db.reminders().all().single { it.key == row.key }.status)
        assertTrue(manager.nextAlarmClock.triggerTime > System.currentTimeMillis())
    }
}
