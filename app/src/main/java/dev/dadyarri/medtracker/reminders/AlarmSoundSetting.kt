package dev.dadyarri.medtracker.reminders

import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.dadyarri.medtracker.R
import kotlinx.coroutines.*
import org.koin.compose.koinInject

@Composable
internal fun AlarmSoundSetting(store: AlarmToneStore = koinInject()) {
    val context = LocalContext.current
    val uri by store.tone.collectAsState(initial = AlarmToneStore.defaultUri())
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("По умолчанию") }
    LaunchedEffect(uri) {
        title = withContext(Dispatchers.IO) {
            try { RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "По умолчанию" }
            catch (_: Exception) { "По умолчанию" }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)?.let { picked ->
                scope.launch {
                    try { store.save(picked); error = null }
                    catch (_: Exception) { error = "Не удалось сохранить мелодию." }
                }
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_bell), null, tint = MaterialTheme.colorScheme.primary)
                Text("Звук будильника", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = {
                try {
                    picker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, AlarmToneStore.defaultUri())
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Звук будильника"))
                } catch (_: RuntimeException) { error = "Системный выбор мелодии недоступен." }
            }) { Text(title) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
internal fun ReminderPermissionStatus() {
    val context = LocalContext.current
    var missing by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    LifecycleResumeEffect(Unit) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        val alarms = context.getSystemService(AlarmManager::class.java)
        dev.dadyarri.medtracker.alarm.AlarmNotifications(context)
        missing = buildList {
            if (!alarms.canScheduleExactAlarms()) add("Точные будильники" to Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            if (!notifications.areNotificationsEnabled() ||
                notifications.getNotificationChannel(dev.dadyarri.medtracker.alarm.AlarmNotifications.SOUND_CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
                add("Уведомления" to Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            if (!notifications.canUseFullScreenIntent()) add("Полноэкранный режим" to Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
        }
        onPauseOrDispose {}
    }
    if (missing.isNotEmpty()) Card {
        Column(Modifier.padding(20.dp)) {
            Text("Доступ для напоминаний", style = MaterialTheme.typography.titleMedium)
            missing.forEach { (label, action) ->
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(action).apply {
                            if (action == Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            else data = "package:${context.packageName}".toUri()
                        })
                    } catch (_: RuntimeException) {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
                    }
                }) { Text(label) }
            }
        }
    }
}
