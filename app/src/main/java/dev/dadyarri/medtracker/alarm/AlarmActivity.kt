package dev.dadyarri.medtracker.alarm

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Only this alarm task may cover the keyguard. It never dismisses authentication. */
class AlarmActivity : ComponentActivity() {
    private val controller: AlarmController by inject()
    private var currentAlarm by mutableStateOf<AlarmRecord?>(null)
    private var busy by mutableStateOf(false)
    private var observer: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Spacer(Modifier.height(24.dp))
                        Text("MedTracker", style = MaterialTheme.typography.titleLarge)
                        Text("Напоминание", style = MaterialTheme.typography.headlineLarge)
                        Text(remember { ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) },
                            style = MaterialTheme.typography.displayLarge)
                        val alarm = currentAlarm
                        if (alarm == null) Text("Проверка сигнала…")
                        else {
                            Text("Отключение звука сохраняет автоматические повторы.")
                            Button(onClick = { act(AlarmController.MUTE) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Отключить звук") }
                            val canSnooze = alarm.snoozes < AlarmPolicy.MAX_SNOOZES &&
                                System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L < alarm.endAt
                            FilledTonalButton(onClick = { act(AlarmController.SNOOZE) }, enabled = !busy && canSnooze,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Отложить на ${alarm.snoozeMinutes} мин") }
                            OutlinedButton(onClick = { act(AlarmController.CANCEL) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Отменить напоминание") }
                            Text("Отложений: ${alarm.snoozes}/${AlarmPolicy.MAX_SNOOZES}")
                        }
                    }
                }
            }
        }
        observe(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        observe(intent)
    }

    private fun observe(intent: Intent) {
        observer?.cancel()
        currentAlarm = null
        val token = intent.getStringExtra(AlarmController.TOKEN) ?: run { closeAlarm(); return }
        observer = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    controller.dao.observe().collect { row ->
                        // PendingIntent extras identify a candidate, never its current state.
                        if (row == null || row.token != token || !row.sound ||
                            row.status != AlarmStatus.RINGING.name || System.currentTimeMillis() >= row.endAt) {
                            closeAlarm()
                        } else {
                            currentAlarm = row
                            setShowWhenLocked(true)
                            setTurnScreenOn(true)
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                  catch (_: Exception) { closeAlarm() }
            }
        }
    }

    private fun act(action: String) {
        val token = currentAlarm?.token ?: return
        if (busy) return
        busy = true
        // Saving emits a new Room state and closes this activity. The operation
        // must outlive its UI so snooze still reaches AlarmManager after that save.
        controller.scope.launch {
            try { controller.action(action, token) }
            catch (_: Exception) { controller.message.value = "Не удалось выполнить действие. Повторите из уведомления." }
            finally { withContext(Dispatchers.Main.immediate) { busy = false } }
        }
    }

    private fun closeAlarm() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(false)
        setTurnScreenOn(false)
        finishAndRemoveTask()
    }
}
