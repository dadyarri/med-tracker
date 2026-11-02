package dev.dadyarri.medtracker.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.dadyarri.medtracker.MainActivity
import dev.dadyarri.medtracker.R

class AlarmNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init {
        manager.createNotificationChannel(NotificationChannel(SOUND_CHANNEL, "Тестовые будильники", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Звук воспроизводится службой с громкостью будильника"
            setSound(null, null)
        })
        manager.createNotificationChannel(NotificationChannel(QUIET_CHANNEL, "Тихие тесты", NotificationManager.IMPORTANCE_DEFAULT).apply {
            setSound(null, null)
        })
    }

    private fun action(action: String, token: String) = PendingIntent.getBroadcast(context, action.hashCode(),
        Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(AlarmController.TOKEN, token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun build(plan: AlarmPlan, allowFullscreen: Boolean): Notification {
        val open = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val ringing = plan.status == AlarmStatus.RINGING
        val builder = NotificationCompat.Builder(context, if (plan.sound && ringing) SOUND_CHANNEL else QUIET_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MedTracker · тестовый сигнал")
            .setContentText(if (ringing) "Тест разрешений и будильников" else "Звук отключён; повторы остаются до отмены")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setOngoing(ringing)
            .setOnlyAlertOnce(true)
            .addAction(0, "Отменить", action(AlarmController.CANCEL, plan.token))
        if (ringing) builder.addAction(0, "Без звука", action(AlarmController.MUTE, plan.token))
        if (plan.snoozes < AlarmPolicy.MAX_SNOOZES)
            builder.addAction(0, "Отложить ${plan.snoozeMinutes} мин", action(AlarmController.SNOOZE, plan.token))
        if (plan.sound && ringing && allowFullscreen && manager.canUseFullScreenIntent()) builder.setFullScreenIntent(open, true)
        return builder.build()
    }

    fun show(plan: AlarmPlan, allowFullscreen: Boolean) {
        try { manager.notify(ID, build(plan, allowFullscreen)) } catch (_: SecurityException) { /* Main screen shows permission state. */ }
    }
    fun cancel() = manager.cancel(ID)

    companion object {
        const val ID = 36
        const val SOUND_CHANNEL = "test_alarm_sound_v1"
        const val QUIET_CHANNEL = "test_alarm_quiet_v1"
    }
}
