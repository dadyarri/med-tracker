package dev.dadyarri.medtracker.alarm

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext

/** Started only by a live exact alarm. Reboot/update only re-register future alarms. */
class AlarmSoundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller by lazy { GlobalContext.get().get<AlarmController>() }
    private var player: MediaPlayer? = null
    private var work: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra(AlarmController.TOKEN) ?: run { stopSelf(); return START_NOT_STICKY }
        work?.cancel()
        player?.release(); player = null
        work = scope.launch {
            try {
                // Validate Room before the FIRST notification (otherwise an initial silent
                // placeholder can consume Android's full-screen notification opportunity).
                // Bound this read below the foreground-service startup deadline.
                val plan = withTimeout(3_000) { controller.ringingPlan(token) }
                    ?: run { stopSelf(startId); return@launch }
                startForeground(AlarmNotifications.ID, AlarmNotifications(this@AlarmSoundService).build(plan, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                player = MediaPlayer()
                player!!.apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setWakeMode(this@AlarmSoundService, PowerManager.PARTIAL_WAKE_LOCK)
                    setDataSource(this@AlarmSoundService, uri)
                    isLooping = true
                    setOnErrorListener { _, _, _ ->
                        controller.scope.launch { controller.audioFailure(token); controller.action(AlarmController.MUTE, token) }
                        true
                    }
                    prepare()
                    start()
                }
                // Bound a single audible burst; repeat/snooze remain managed by AlarmManager.
                delay(60_000)
                controller.scope.launch { controller.action(AlarmController.MUTE, token) }
            } catch (_: TimeoutCancellationException) {
                controller.scope.launch { controller.audioFailure(token) }
                stopSelf(startId)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) {
                controller.scope.launch {
                    controller.audioFailure(token)
                    controller.action(AlarmController.MUTE, token)
                }
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_DETACH)
        work?.cancel(); scope.cancel()
        player?.release(); player = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
