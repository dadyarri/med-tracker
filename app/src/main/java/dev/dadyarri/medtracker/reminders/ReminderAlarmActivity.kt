package dev.dadyarri.medtracker.reminders

import android.app.KeyguardManager
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.dadyarri.medtracker.R
import dev.dadyarri.medtracker.alarm.AlarmController
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.time.*
import java.time.format.DateTimeFormatter

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
                ReminderAlarmScreen(rows, busy, ::openEntry, ::act)
            }
        }
        observe(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); observe(intent) }
    private fun observe(intent: Intent) {
        observer?.cancel()
        val token = runCatching { intent.getStringExtra(AlarmController.TOKEN) }.getOrNull()
            ?.takeIf { it.isNotBlank() && it.length <= 512 } ?: run { close(); return }
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
    private fun openEntry(row: ReminderOccurrence) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        val open = { startActivity(reminderEntryIntent(this, row)) }
        if (keyguard.isKeyguardLocked) keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() { open() }
        }) else open()
    }
    private fun act(action: String, key: String?) {
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

@Composable
internal fun ReminderAlarmScreen(rows: List<ReminderOccurrence>, busy: Boolean,
                                 onRecord: (ReminderOccurrence) -> Unit, onAction: (String, String?) -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Напоминания", style = MaterialTheme.typography.titleLarge)
            rows.firstOrNull()?.let { row ->
                Text(Instant.ofEpochMilli(row.dueAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.displayLarge)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Card(shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(painterResource(row.icon), null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                                Text(row.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            }
                            Button(enabled = !busy, onClick = { onRecord(row) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(row.entryLabel) }
                            TextButton(enabled = !busy, onClick = { onAction(AlarmController.CANCEL, row.key) }) { Text("Пропустить") }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = !busy && rows.any { it.status == "RINGING" },
                    modifier = Modifier.weight(1f), onClick = { onAction(AlarmController.MUTE, null) }) {
                    Icon(painterResource(R.drawable.ic_bell), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Без звука")
                }
                OutlinedButton(enabled = !busy && rows.any { it.snoozes < 3 && System.currentTimeMillis() + 300_000 < it.endAt },
                    modifier = Modifier.weight(1f), onClick = { onAction(AlarmController.SNOOZE, null) }) {
                    Icon(painterResource(R.drawable.ic_clock), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("5 мин")
                }
            }
        }
    }
}
