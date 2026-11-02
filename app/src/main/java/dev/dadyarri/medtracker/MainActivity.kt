package dev.dadyarri.medtracker

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dadyarri.medtracker.ui.AlarmViewModel
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = colors) { MedTrackerApp() }
        }
    }
}

private enum class Destination(val title: String, val icon: ImageVector) {
    TODAY("Сегодня", Icons.Default.DateRange),
    HISTORY("История", Icons.AutoMirrored.Filled.List),
    MEDICATIONS("Лекарства", Icons.Default.Favorite),
    SETTINGS("Настройки", Icons.Default.Settings),
}

private data class PermissionStep(val title: String, val description: String, val icon: ImageVector)

private val permissionSteps = listOf(
    PermissionStep("Уведомления", "Показывают напоминания и позволяют быстро отложить или отключить сигнал.", Icons.Default.Notifications),
    PermissionStep("Точное время", "Разрешите будильники, чтобы напоминания приходили в назначенное время.", Icons.Default.DateRange),
    PermissionStep("Экран напоминания", "Позволяет показывать звуковое напоминание поверх экрана блокировки.", Icons.Default.Lock),
    PermissionStep("Работа в фоне", "Исключение из оптимизации батареи помогает напоминаниям работать, когда приложение закрыто.", Icons.Default.Info),
    PermissionStep("Настройки Samsung", "Добавьте MedTracker в список никогда не спящих приложений, чтобы One UI не ограничивала напоминания.", Icons.Default.CheckCircle),
)

@Composable
private fun MedTrackerApp(model: AlarmViewModel = koinViewModel()) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val permissions by model.permissions.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val context = LocalContext.current
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.refresh()
        }
        lifecycle.lifecycle.addObserver(observer)
        model.refresh()
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    fun open(action: String, packageUri: Boolean = true) {
        try {
            context.startActivity(Intent(action).apply {
                if (packageUri) data = "package:${context.packageName}".toUri()
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            })
        } catch (_: RuntimeException) {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            } catch (_: RuntimeException) {
                model.message.value = "Откройте настройки приложения вручную."
            }
        }
    }
    val setup = settings
    when {
        setup == null -> Surface(Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        !setup.first -> PermissionWizard(
            granted = listOf(permissions.notifications, permissions.exact, permissions.fullscreen, permissions.battery, setup.second),
            busy = busy,
            message = message,
            onRequest = { step ->
                when (step) {
                    0 -> if (!permissions.notifications) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                         else open(Settings.ACTION_APP_NOTIFICATION_SETTINGS, false)
                    1 -> open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    2 -> open(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    3 -> {
                        // Direct exemption is required for this personal sideloaded app.
                        @Suppress("BatteryLife")
                        val action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                        open(action)
                    }
                    4 -> open(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, false)
                }
            },
            onSamsungConfirmed = model::samsungConfirmed,
            onFinish = model::finishWizard,
        )
        else -> EmptyHome()
    }
}

@Composable
internal fun PermissionWizard(
    granted: List<Boolean>,
    busy: Boolean,
    message: String?,
    onRequest: (Int) -> Unit,
    onSamsungConfirmed: (Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val item = permissionSteps[step]
    BackHandler(enabled = step > 0) { step-- }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text("Настройка напоминаний", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { (step + 1f) / permissionSteps.size }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("Шаг ${step + 1} из ${permissionSteps.size}", style = MaterialTheme.typography.labelLarge)
            key(step) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(item.icon, contentDescription = null, Modifier.padding(20.dp).size(40.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Text(item.title, style = MaterialTheme.typography.headlineLarge)
                    Text(item.description, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (granted[step]) {
                            if (step == 4) "Вы подтвердили настройку вручную" else "Разрешение предоставлено"
                        } else if (step == 4) "Нужна ручная проверка" else "Разрешение не предоставлено",
                        color = if (granted[step]) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (step == 4) {
                        Text("Настройки → Обслуживание устройства → Батарея → Ограничения фонового использования → Никогда не спящие приложения.")
                        Text("Названия пунктов могут отличаться. Android не позволяет приложению проверить этот список.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = granted[4], onCheckedChange = onSamsungConfirmed, enabled = !busy)
                            Text("Я добавил MedTracker в список", Modifier.weight(1f))
                        }
                    }
                    OutlinedButton(onClick = { onRequest(step) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (granted[step] || step == 4) "Открыть настройки" else "Предоставить доступ")
                    }
                    Text("Можно продолжить без разрешения и настроить его позже в системных настройках приложения.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) OutlinedButton(onClick = { step-- }, enabled = !busy) { Text("Назад") }
                Button(
                    onClick = { if (step == permissionSteps.lastIndex) onFinish() else step++ },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (step == permissionSteps.lastIndex) "Открыть MedTracker" else if (granted[step]) "Продолжить" else "Пропустить")
                }
            }
        }
    }
}

@Composable
internal fun EmptyHome() {
    var selected by rememberSaveable { mutableStateOf(Destination.TODAY.name) }
    val destination = Destination.valueOf(selected)
    BackHandler(enabled = destination != Destination.TODAY) { selected = Destination.TODAY.name }
    Scaffold(
        topBar = {
            Surface {
                Text(destination.title, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(24.dp))
            }
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == destination,
                        onClick = { selected = item.name },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.title) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
    }
}
