package dev.dadyarri.medtracker.reminders

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import dev.dadyarri.medtracker.R
import dev.dadyarri.medtracker.alarm.*

class ReminderNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    init { AlarmNotifications(context) }
    private fun action(action: String, token: String) = PendingIntent.getBroadcast(context, action.hashCode(),
        Intent(context, ReminderAlarmReceiver::class.java).setAction(action)
            .putExtra(AlarmController.TOKEN, token),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun build(rows: List<ReminderOccurrence>, fullscreen: Boolean): Notification {
        require(rows.isNotEmpty())
        val token = rows.first().token
        val ringing = rows.any { it.status == "RINGING" }
        val intent = Intent(context, ReminderAlarmActivity::class.java)
            .setData(android.net.Uri.Builder().scheme("medtracker").authority("reminder").appendPath(token).build())
            .putExtra(AlarmController.TOKEN, token)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val options = ActivityOptions.makeBasic().setPendingIntentCreatorBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
        val open = PendingIntent.getActivity(context, ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE, options.toBundle())
        val builder = NotificationCompat.Builder(context, if (ringing) AlarmNotifications.SOUND_CHANNEL else AlarmNotifications.QUIET_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("MedTracker · напоминания")
            .setContentText(rows.joinToString(" · ") { it.title })
            .setStyle(NotificationCompat.BigTextStyle().bigText(rows.joinToString("\n") { it.title }))
            .setCategory(NotificationCompat.CATEGORY_ALARM).setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open).setOngoing(ringing).setOnlyAlertOnce(!fullscreen)
            .addAction(0, "Пропустить", action(AlarmController.CANCEL, token))
        if (ringing) builder.addAction(0, "Без звука", action(AlarmController.MUTE, token))
        if (rows.any { it.snoozes < 3 && System.currentTimeMillis() + 5 * 60_000 < it.endAt })
            builder.addAction(0, "Отложить 5 мин", action(AlarmController.SNOOZE, token))
        if (ringing && fullscreen && manager.canUseFullScreenIntent()) builder.setFullScreenIntent(open, true)
        return builder.build()
    }
    fun show(rows: List<ReminderOccurrence>, fullscreen: Boolean) {
        if (rows.isEmpty()) { cancel(); return }
        try { manager.notify(ID, build(rows, fullscreen)) } catch (_: SecurityException) { /* Settings show permission state. */ }
    }
    fun cancel() = manager.cancel(ID)
    companion object { const val ID = 72 }
}
