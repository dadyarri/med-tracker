package dev.dadyarri.medtracker.reminders

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
import androidx.lifecycle.lifecycleScope
import dev.dadyarri.medtracker.MainActivity
import dev.dadyarri.medtracker.alarm.AlarmController
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

class ReminderAlarmActivity : ComponentActivity() {
    private val controller: ReminderAlarmController by inject()
    private var rows by mutableStateOf(emptyList<ReminderOccurrence>())
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
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Напоминания", style = MaterialTheme.typography.headlineLarge)
                        rows.forEach { row ->
                            Card(shape = MaterialTheme.shapes.extraLarge) {
                                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                    Text(row.title, style = MaterialTheme.typography.headlineSmall)
                                    Text(java.time.Instant.ofEpochMilli(row.dueAt).atZone(java.time.ZoneId.systemDefault())
                                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                                        style = MaterialTheme.typography.displaySmall)
                                    TextButton(enabled = !busy, onClick = { act(AlarmController.CANCEL, row.key) }) { Text("Пропустить") }
                                }
                            }
                        }
                        Button(enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            onClick = { startActivity(Intent(this@ReminderAlarmActivity, MainActivity::class.java)) }) { Text("Открыть журнал") }
                        FilledTonalButton(enabled = !busy && rows.any { it.status == "RINGING" },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            onClick = { act(AlarmController.MUTE) }) { Text("Отключить звук") }
                        OutlinedButton(enabled = !busy && rows.any { it.snoozes < 3 && System.currentTimeMillis() + 300_000 < it.endAt },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            onClick = { act(AlarmController.SNOOZE) }) { Text("Отложить на 5 мин") }
                    }
                }
            }
        }
        observe(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); observe(intent) }
    private fun observe(intent: Intent) {
        observer?.cancel()
        val token = intent.getStringExtra(AlarmController.TOKEN) ?: run { close(); return }
        observer = lifecycleScope.launch {
            controller.dao.observe().collect { values ->
                rows = values.presentation(token)
                if (rows.isEmpty()) close()
                else {
                    val ringing = rows.any { it.status == "RINGING" }
                    setShowWhenLocked(true); setTurnScreenOn(ringing)
                    if (ringing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }
    private fun act(action: String, key: String? = null) {
        val token = rows.firstOrNull()?.token ?: return
        if (busy) return
        busy = true
        controller.scope.launch {
            try { controller.action(action, token, key) }
            catch (_: Exception) { controller.message.value = "Не удалось выполнить действие." }
            finally { withContext(Dispatchers.Main.immediate) { busy = false } }
        }
    }
    private fun close() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(false); setTurnScreenOn(false)
        finishAndRemoveTask()
    }
}
