package dev.dadyarri.medtracker.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import dev.dadyarri.medtracker.reminders.AlarmToneStore
import kotlinx.coroutines.*

/** Preparing providers and media never blocks the UI thread. Cancellation releases the player. */
internal suspend fun prepareAlarmAudio(context: Context, uri: Uri): MediaPlayer {
    val media = withContext(Dispatchers.IO + NonCancellable) {
        fun prepare(selected: Uri): MediaPlayer {
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                player.setDataSource(context, selected)
                player.isLooping = true
                player.prepare()
                return player
            } catch (failure: Exception) { player.release(); throw failure }
        }
        try { prepare(uri) } catch (_: Exception) { prepare(AlarmToneStore.defaultUri()) }
    }
    if (!currentCoroutineContext().isActive) { media.release(); currentCoroutineContext().ensureActive() }
    return media
}
