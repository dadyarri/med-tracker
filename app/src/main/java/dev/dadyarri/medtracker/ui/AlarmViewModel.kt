package dev.dadyarri.medtracker.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import android.app.Application
import androidx.lifecycle.viewModelScope
import dev.dadyarri.medtracker.alarm.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val Context.preferences by preferencesDataStore("alarm_setup")
private val WIZARD = booleanPreferencesKey("wizard_finished")
private val SAMSUNG = booleanPreferencesKey("samsung_manually_confirmed")

data class Permissions(
    val notifications: Boolean = false, val exact: Boolean = false,
    val fullscreen: Boolean = false, val battery: Boolean = false,
    val alarmChannel: Boolean = false, val quietChannel: Boolean = false,
    val volume: Int = 0, val maxVolume: Int = 0, val dnd: Boolean = false,
)

class AlarmViewModel(application: Application, private val controller: AlarmController) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>().applicationContext
    val alarm = controller.dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val events = controller.dao.events().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val message = controller.message
    val permissions = MutableStateFlow(Permissions())
    val settings = context.preferences.data.catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { Pair(it[WIZARD] ?: false, it[SAMSUNG] ?: false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val busy = MutableStateFlow(false)

    fun refresh() {
        val manager = context.getSystemService(NotificationManager::class.java)
        AlarmNotifications(context)
        val audio = context.getSystemService(AudioManager::class.java)
        permissions.value = Permissions(
            notifications = manager.areNotificationsEnabled(),
            exact = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            fullscreen = manager.canUseFullScreenIntent(),
            battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            alarmChannel = manager.getNotificationChannel(AlarmNotifications.SOUND_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE,
            quietChannel = manager.getNotificationChannel(AlarmNotifications.QUIET_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE,
            volume = audio.getStreamVolume(AudioManager.STREAM_ALARM),
            maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            dnd = manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        execute { controller.reconcile() }
    }

    fun execute(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block() }
            catch (_: Exception) { message.value = "Операция не выполнена. Повторите действие; состояние сохранено локально." }
            finally { busy.value = false }
        }
    }
    fun schedule(delay: Int, sound: Boolean, repeat: Int, window: Int, snooze: Int) =
        execute { controller.create(delay, sound, repeat, window, snooze) }
    fun action(action: String) = execute { controller.action(action) }
    fun finishWizard() = execute { context.preferences.edit { it[WIZARD] = true } }
    fun samsungConfirmed(value: Boolean) = execute { context.preferences.edit { it[SAMSUNG] = value } }
}
