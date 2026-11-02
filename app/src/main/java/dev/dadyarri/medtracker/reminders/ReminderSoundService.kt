package dev.dadyarri.medtracker.reminders

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import dev.dadyarri.medtracker.alarm.prepareAlarmAudio
import android.os.*
import dev.dadyarri.medtracker.alarm.AlarmController
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext

class ReminderSoundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { GlobalContext.get().get<ReminderAlarmController>() }
    private var player: MediaPlayer? = null
    private var work: Job? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra(AlarmController.TOKEN) ?: run { stopSelf(); return START_NOT_STICKY }
        work?.cancel()
        player?.release(); player = null
        work = scope.launch {
            try {
                val rows = withTimeout(3_000) { controller.ringing(token) }
                if (rows.isEmpty()) { stopSelf(startId); return@launch }
                startForeground(ReminderNotifications.ID, ReminderNotifications(this@ReminderSoundService).build(rows, true),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                val uri = withContext(Dispatchers.IO) { GlobalContext.get().get<AlarmToneStore>().current() }
                player = prepareAlarmAudio(this@ReminderSoundService, uri)
                player?.setOnErrorListener { _, _, _ ->
                    controller.scope.launch { controller.action(AlarmController.MUTE, token) }
                    true
                }
                player?.start()
                delay(60_000)
                controller.scope.launch { controller.action(AlarmController.MUTE, token) }
            } catch (_: TimeoutCancellationException) {
                controller.message.value = "Не удалось вовремя открыть напоминание. Вернитесь в приложение."
                stopSelf(startId)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                controller.message.value = "Не удалось воспроизвести звук. Проверьте громкость будильников."
                controller.scope.launch { controller.action(AlarmController.MUTE, token) }
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_DETACH)
        work?.cancel(); scope.cancel(); player?.release(); player = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
