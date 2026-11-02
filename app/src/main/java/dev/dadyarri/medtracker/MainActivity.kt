package dev.dadyarri.medtracker

import android.Manifest
import android.content.Intent
import androidx.core.net.toUri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dadyarri.medtracker.alarm.*
import dev.dadyarri.medtracker.ui.AlarmViewModel
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = colors) { AlarmScreen() }
        }
    }
}

private fun time(at: Long) = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

@Composable
private fun AlarmScreen(model: AlarmViewModel = koinViewModel()) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val permissions by model.permissions.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val alarm by model.alarm.collectAsStateWithLifecycle()
    val events by model.events.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var delay by rememberSaveable { mutableStateOf("30") }
    var repeat by rememberSaveable { mutableStateOf("15") }
    var window by rememberSaveable { mutableStateOf("120") }
    var snooze by rememberSaveable { mutableStateOf("5") }
    var sound by rememberSaveable { mutableStateOf(true) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        lifecycle.lifecycle.addObserver(observer)
        model.refresh()
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun open(action: String, packageUri: Boolean = false, channel: String? = null) {
        val intent = Intent(action).apply {
            if (packageUri) data = "package:${context.packageName}".toUri()
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            if (channel != null) putExtra(Settings.EXTRA_CHANNEL_ID, channel)
        }
        try { context.startActivity(intent) }
        catch (_: RuntimeException) {
            try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
            catch (_: RuntimeException) { model.message.value = "Экран недоступен. Откройте настройки приложения вручную." }
        }
    }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("MedTracker", style = MaterialTheme.typography.headlineLarge)
            Text("Проверка разрешений и будильников", style = MaterialTheme.typography.titleMedium)
            Text("Тестовый этап · без лекарств и измерений")
            alarm?.takeIf { it.status in setOf(AlarmStatus.RINGING.name, AlarmStatus.MUTED.name) }?.let { active ->
                Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Тестовый сигнал требует действия", style = MaterialTheme.typography.headlineSmall)
                    Text("Отключение звука не отменяет будущие повторы.")
                    Button(enabled = !busy && active.status == AlarmStatus.RINGING.name,
                        onClick = { model.action(AlarmController.MUTE) }) { Text("Отключить звук") }
                    Button(enabled = !busy && active.snoozes < 3,
                        onClick = { model.action(AlarmController.SNOOZE) }) { Text("Отложить на ${active.snoozeMinutes} мин") }
                    OutlinedButton(enabled = !busy, onClick = { model.action(AlarmController.CANCEL) }) { Text("Отменить тест и повторы") }
                } }
            }
            if (!settings.first || setupOpen) {
                Text("Мастер разрешений", style = MaterialTheme.typography.headlineSmall)
                Text("Разрешения можно пропустить. При возврате из настроек статусы обновляются; отзыв доступа виден здесь.")
                PermissionCard("1. Уведомления", permissions.notifications, "Для отображения сигнала и действий.") {
                    if (!permissions.notifications) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else open(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                }
                TextButton(onClick = { open(Settings.ACTION_APP_NOTIFICATION_SETTINGS) }) { Text("Системные настройки уведомлений") }
                PermissionCard("2. Точные будильники", permissions.exact, "Единый механизм setAlarmClock для звуковых и тихих тестов.") {
                    open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri = true)
                }
                PermissionCard("3. Полноэкранные уведомления", permissions.fullscreen, "Android выбирает экран блокировки или уведомление. Тихий тест не открывает экран.") {
                    open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri = true)
                }
                PermissionCard("4. Исключение оптимизации батареи", permissions.battery, "Предлагается сразу; не обходит все ограничения One UI.") {
                    open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri = true)
                }
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("5. Samsung: никогда не спящие приложения", style = MaterialTheme.typography.titleMedium)
                    Text("Настройки → Обслуживание устройства → Батарея → Ограничения фонового использования → Никогда не спящие приложения. Добавьте MedTracker. Названия в One UI могут отличаться.")
                    Text("Проверка только вручную: Android не предоставляет надёжного API для этого списка.")
                    Row { Checkbox(checked = settings.second, enabled = !busy, onCheckedChange = model::samsungConfirmed)
                        Text("Я проверил список Samsung вручную", Modifier.padding(top = 12.dp)) }
                    OutlinedButton(onClick = { open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) }) { Text("Открыть настройки батареи") }
                } }
                Button(enabled = !busy, onClick = { model.finishWizard(); setupOpen = false }) { Text("Перейти к тесту, даже без разрешений") }
            } else OutlinedButton(onClick = { setupOpen = true }) { Text("Открыть мастер разрешений") }
            Text("Диагностика", style = MaterialTheme.typography.headlineSmall)
            Text("Уведомления: ${yesNo(permissions.notifications)} · точные: ${yesNo(permissions.exact)}\nПолный экран: ${yesNo(permissions.fullscreen)} · батарея: ${yesNo(permissions.battery)}")
            Text("Каналы: будильники ${yesNo(permissions.alarmChannel)}, тихие ${yesNo(permissions.quietChannel)}\nГромкость будильников: ${permissions.volume}/${permissions.maxVolume} · «Не беспокоить»: ${if (permissions.dnd) "включён" else "выключен"}")
            TextButton(onClick = { open(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channel = AlarmNotifications.SOUND_CHANNEL) }) { Text("Канал будильников") }
            TextButton(onClick = { open(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, channel = AlarmNotifications.QUIET_CHANNEL) }) { Text("Канал тихих уведомлений") }
            TextButton(onClick = { open(Settings.ACTION_SOUND_SETTINGS) }) { Text("Громкость и звук") }
            if (!permissions.notifications || !permissions.exact || !permissions.alarmChannel)
                Text("Часть разрешений отозвана или не предоставлена. Тест может не прозвучать/не отобразиться. Экран приложения остаётся доступен.", color = MaterialTheme.colorScheme.error)
            message?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            Text("Тестовый сигнал", style = MaterialTheme.typography.headlineSmall)
            Text("Один тест за раз: новая постановка заменяет предыдущую. Звук автоматически отключается через 60 секунд; это не завершает тест. Отмена завершает тест и повторы.")
            NumberField("Через сколько секунд (10–3600)", delay) { delay = it }
            Row { Switch(checked = sound, onCheckedChange = { sound = it }); Text("Звуковой сигнал", Modifier.padding(12.dp)) }
            NumberField("Автоповтор, минуты (1–60)", repeat) { repeat = it }
            NumberField("Окно повторов, минуты (1–240)", window) { window = it }
            NumberField("Отложить на, минуты (1–30; максимум 3 раза)", snooze) { snooze = it }
            val valid = (delay.toIntOrNull() ?: 0) in 10..3600 && (repeat.toIntOrNull() ?: 0) in 1..60 &&
                (window.toIntOrNull() ?: 0) in 1..240 && (snooze.toIntOrNull() ?: 0) in 1..30
            Button(enabled = valid && !busy, onClick = { model.schedule(delay.toInt(), sound, repeat.toInt(), window.toInt(), snooze.toInt()) }) { Text("Назначить / заменить тест") }
            if (!valid) Text("Проверьте значения и диапазоны", color = MaterialTheme.colorScheme.error)
            alarm?.let { row ->
                val status = AlarmStatus.valueOf(row.status)
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Состояние: ${statusLabel(status)}", style = MaterialTheme.typography.titleMedium)
                    Text("Следующий сигнал: ${row.nextAt?.let(::time) ?: "нет"}\nОкно до: ${time(row.endAt)}\nОтложений: ${row.snoozes}/3")
                    row.diagnostic?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (status == AlarmStatus.RINGING || status == AlarmStatus.MUTED) {
                        Button(enabled = !busy && status == AlarmStatus.RINGING, onClick = { model.action(AlarmController.MUTE) }) { Text("Отключить звук") }
                        Button(enabled = !busy && row.snoozes < 3, onClick = { model.action(AlarmController.SNOOZE) }) { Text("Отложить на ${row.snoozeMinutes} мин") }
                    }
                    OutlinedButton(enabled = !busy && status !in setOf(AlarmStatus.CANCELLED, AlarmStatus.EXPIRED), onClick = { model.action(AlarmController.CANCEL) }) { Text("Отменить тест и повторы") }
                } }
            }
            OutlinedButton(enabled = !busy, onClick = model::refresh) { Text("Обновить статусы / согласовать") }
            Text("Журнал последних событий", style = MaterialTheme.typography.headlineSmall)
            if (events.isEmpty()) Text("Тест ещё не назначался")
            events.forEach { Text("${time(it.at)} · ${it.description}") }
            Text("После перезагрузки и обновления APK восстанавливаются будущие сигналы; просроченные не звучат. После принудительной остановки в настройках Android нужно снова открыть приложение. Выключенный телефон не подаёт сигнал.")
            Text("Удаление приложения или очистка данных удаляет тест и журнал. Обновляйте APK поверх установленного с той же подписью.")
        }
    }
}
private fun yesNo(value: Boolean) = if (value) "разрешено" else "нет доступа"
private fun statusLabel(status: AlarmStatus) = when (status) {
    AlarmStatus.WAITING -> "ожидается"
    AlarmStatus.SNOOZED -> "отложен"
    AlarmStatus.RINGING -> "требует действия"
    AlarmStatus.MUTED -> "звук отключён, требует действия"
    AlarmStatus.CANCELLED -> "отменён"
    AlarmStatus.EXPIRED -> "срок истёк / пропущен"
}
@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}
@Composable
private fun PermissionCard(title: String, granted: Boolean, detail: String, onClick: () -> Unit) {
    Card { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(if (granted) "Разрешено" else "Не предоставлено / отозвано")
        Text(detail)
        OutlinedButton(onClick = onClick) { Text("Открыть / запросить") }
    } }
}
