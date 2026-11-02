package dev.dadyarri.medtracker

import android.app.ActivityOptions
import android.app.Application
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import dev.dadyarri.medtracker.alarm.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowNotificationManager

/** Simulates the user-controlled full-screen permission; not OEM UI behavior. */
@Implements(NotificationManager::class)
class FullScreenPermissionShadow : ShadowNotificationManager() {
    @Implementation protected fun canUseFullScreenIntent(): Boolean = allowed
    companion object { var allowed = true }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MedTrackerApplication::class, shadows = [FullScreenPermissionShadow::class])
class AlarmPresentationTest {
    private lateinit var application: Application
    private lateinit var controller: AlarmController
    private lateinit var db: AlarmDatabase
    private fun ringing(): AlarmPlan {
        val now = System.currentTimeMillis()
        val plan = AlarmPolicy.create(now - 30_000, 30, true, "initial")
        return AlarmPolicy.deliver(plan, "initial", now, "live")!!
    }
    @Before fun setUp() {
        application = RuntimeEnvironment.getApplication()
        controller = GlobalContext.get().get()
        db = GlobalContext.get().get()
        FullScreenPermissionShadow.allowed = true
    }
    @After fun tearDown() {
        controller.scope.cancel()
        db.close()
        stopKoin()
    }

    @Test fun fullScreenIntentTargetsPrivateAlarmTaskWithAndroid16CreatorOptIn() {
        val notification = AlarmNotifications(application).build(ringing(), true)
        assertNotNull(notification.fullScreenIntent)
        val pending = shadowOf(notification.fullScreenIntent)
        assertEquals(AlarmActivity::class.java.name, pending.savedIntent.component!!.className)
        assertEquals("live", pending.savedIntent.getStringExtra(AlarmController.TOKEN))
        val options = org.robolectric.util.ReflectionHelpers.callStaticMethod<ActivityOptions>(
            ActivityOptions::class.java, "fromBundle",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.os.Bundle::class.java, pending.options))
        assertEquals(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS,
            options.pendingIntentCreatorBackgroundActivityStartMode)
        assertEquals(0, notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }
    @Test fun deniedFullScreenStillProvidesClickableAlarmNotification() {
        FullScreenPermissionShadow.allowed = false
        val notification = AlarmNotifications(application).build(ringing(), true)
        assertNull(notification.fullScreenIntent)
        assertEquals(AlarmActivity::class.java.name, shadowOf(notification.contentIntent).savedIntent.component!!.className)
        assertEquals(NotificationManager.IMPORTANCE_HIGH,
            application.getSystemService(NotificationManager::class.java).getNotificationChannel(notification.channelId).importance)
    }
    @Test fun quietAndMutedNotificationsDoNotOpenAlarmActivity() {
        for (plan in listOf(ringing().copy(sound = false), ringing().copy(status = AlarmStatus.MUTED))) {
            val notification = AlarmNotifications(application).build(plan, true)
            assertNull(notification.fullScreenIntent)
            assertEquals(MainActivity::class.java.name, shadowOf(notification.contentIntent).savedIntent.component!!.className)
        }
    }
    @Test fun newAlertingChannelDoesNotDependOnImmutableOldSilentChannel() {
        val manager = application.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("test_alarm_sound_v1", "Old", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null); enableVibration(false)
        })
        AlarmNotifications(application)
        assertFalse(manager.getNotificationChannel("test_alarm_sound_v1").shouldVibrate())
        assertEquals("Звуковые напоминания", manager.getNotificationChannel("test_alarm_sound_v1").name)
        val current = manager.getNotificationChannel(AlarmNotifications.SOUND_CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, current.importance)
        assertTrue(current.shouldVibrate())
        assertEquals("Звуковые напоминания", current.name)
        assertEquals("Тихие напоминания", manager.getNotificationChannel(AlarmNotifications.QUIET_CHANNEL).name)
        assertNull(current.sound) // Service owns audio; no duplicate ringtone.
    }
    @Test fun alarmCoversSecureKeyguardAndClosesAfterCancellationWithoutUnlocking() = runBlocking {
        val keyguard = application.getSystemService(KeyguardManager::class.java)
        shadowOf(keyguard).setKeyguardLocked(true)
        shadowOf(keyguard).setIsDeviceLocked(true)
        shadowOf(keyguard).setIsKeyguardSecure(true)
        val plan = ringing()
        db.alarms().save(plan.record())
        val activity = Robolectric.buildActivity(AlarmActivity::class.java,
            Intent(application, AlarmActivity::class.java).putExtra(AlarmController.TOKEN, plan.token)).setup()
        try {
            await { shadowOf(activity.get()).showWhenLocked &&
                activity.get().window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }
            assertTrue(shadowOf(activity.get()).turnScreenOn)
            assertTrue(keyguard.isKeyguardLocked)
            assertTrue(keyguard.isDeviceLocked)
            controller.action(AlarmController.CANCEL, plan.token)
            await { activity.get().isFinishing }
            assertTrue(keyguard.isDeviceLocked)
        } finally { activity.pause().stop().destroy() }
    }
    @Test fun snoozeActionCompletesAfterAlarmWindowCloses() = runBlocking {
        org.robolectric.shadows.ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val plan = ringing()
        db.alarms().save(plan.record())
        val activity = Robolectric.buildActivity(AlarmActivity::class.java,
            Intent(application, AlarmActivity::class.java).putExtra(AlarmController.TOKEN, plan.token)).setup()
        try {
            await { shadowOf(activity.get()).showWhenLocked &&
                activity.get().window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0 }
            org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(activity.get(), "act",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(String::class.java, AlarmController.SNOOZE))
            await { activity.get().isFinishing }
            val manager = application.getSystemService(android.app.AlarmManager::class.java)
            await { manager.nextAlarmClock != null }
            assertEquals(AlarmStatus.SNOOZED.name, db.alarms().current()!!.status)
            assertEquals(db.alarms().current()!!.nextAt, manager.nextAlarmClock.triggerTime)
        } finally { activity.pause().stop().destroy() }
    }
    @Test fun staleAlarmActivityFinishesWithoutShowingSettings() {
        val activity = Robolectric.buildActivity(AlarmActivity::class.java,
            Intent(application, AlarmActivity::class.java).putExtra(AlarmController.TOKEN, "stale")).setup()
        try { await { activity.get().isFinishing } }
        finally { activity.pause().stop().destroy() }
    }
    private fun await(condition: () -> Boolean) {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Expected Room/lifecycle state was not observed", condition())
    }
}
