package dev.dadyarri.medtracker.reminders

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dadyarri.medtracker.meals.*
import dev.dadyarri.medtracker.measurements.*
import dev.dadyarri.medtracker.pressure.PressureEditor
import kotlinx.coroutines.flow.map
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

/** A private, translucent task: notification taps open the form directly, never MainActivity. */
class ReminderEntryActivity : ComponentActivity() {
    private val controller: ReminderAlarmController by inject()
    private var request by mutableStateOf<ReminderEntryRequest?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        read(intent)
        setContent {
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = colors) {
                val current = request
                if (current != null) key(current) {
                    val rows by remember { controller.dao.observe().map<List<ReminderOccurrence>, List<ReminderOccurrence>?> { it } }
                        .collectAsStateWithLifecycle(initialValue = null)
                    ReminderEntrySheet(current, rows, onClose = { if (request == current) finishAndRemoveTask() })
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); read(intent) }
    private fun read(intent: Intent) {
        request = ReminderEntryRequest.from(intent)
        if (request == null) finishAndRemoveTask()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderEntrySheet(request: ReminderEntryRequest, rows: List<ReminderOccurrence>?,
                                onClose: () -> Unit, model: ReminderEntryViewModel = koinViewModel(key = "$request")) {
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val meals by model.meals.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by rememberSaveable(request.key, request.revision, request.token) { mutableStateOf(request.key) }
    val candidates = rows.orEmpty().filter(request::accepts)
    val row = candidates.find { it.key == selected } ?: candidates.singleOrNull()
    val completed = if (request.key != null) rows.orEmpty().any {
        it.key == request.key && it.revision == request.revision && it.status == "COMPLETED"
    } else candidates.isEmpty() && rows.orEmpty().any { it.token == request.token && it.status == "COMPLETED" }
    LaunchedEffect(completed) { if (completed) onClose() }
    val close = { if (!busy) onClose() }
    BackHandler(enabled = busy) {}
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = !busy, properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy)) {
        when {
            rows == null -> Box(Modifier.fillMaxWidth().padding(32.dp)) { CircularProgressIndicator() }
            row == null -> Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (candidates.isEmpty()) {
                    Text("Напоминание больше недоступно", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = close) { Text("Закрыть") }
                } else {
                    Text("Что записать?", style = MaterialTheme.typography.headlineSmall)
                    candidates.forEach { candidate ->
                        FilledTonalButton(onClick = { selected = candidate.key; model.error.value = null }, modifier = Modifier.fillMaxWidth()) {
                            Icon(androidx.compose.ui.res.painterResource(candidate.icon), null, Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp)); Text(candidate.title)
                        }
                    }
                }
            }
            else -> key(row.key, row.revision) {
                val now = rememberSaveable { System.currentTimeMillis() }
                val saved = {
                    if (request.key != null || candidates.size <= 1) onClose()
                    else { selected = null; model.error.value = null }
                }
                when (row.entryType) {
                    JournalType.PRESSURE -> PressureEditor(row.entryId, row.pressureInitial(now), false, busy, error,
                        { model.savePressure(row, it, saved) }, {}, close)
                    JournalType.WEIGHT, JournalType.GLUCOSE -> MeasurementEditor(row.entryId, row.measurementDraft(now),
                        false, busy, error, { model.saveMeasurement(row, it, saved) }, {}, close, meals)
                    JournalType.MEAL -> MealEditor(MealEvent(row.entryId, row.meal ?: row.source, now, now, now),
                        busy, { model.saveMeal(row, it, saved) }, {}, editing = false, onClose = close, error = error)
                    null -> Text("Тип записи недоступен", modifier = Modifier.padding(24.dp))
                }
            }
        }
    }
}
