package dev.dadyarri.medtracker.measurements

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.dadyarri.medtracker.EmptyHome
import dev.dadyarri.medtracker.R
import dev.dadyarri.medtracker.pressure.*
import dev.dadyarri.medtracker.ui.ContextBadge
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsHome(
    pressure: PressureViewModel = koinViewModel(),
    model: MeasurementViewModel = koinViewModel(),
    mealsModel: dev.dadyarri.medtracker.meals.MealViewModel? = null,
) {
    val pressures by pressure.records.collectAsStateWithLifecycle()
    val weights by model.weights.collectAsStateWithLifecycle()
    val glucose by model.glucose.collectAsStateWithLifecycle()
    val meals by (mealsModel?.meals ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList<dev.dadyarri.medtracker.meals.MealEvent>())).collectAsStateWithLifecycle()
    val cycle by (mealsModel?.cycle ?: kotlinx.coroutines.flow.MutableStateFlow<dev.dadyarri.medtracker.meals.GlucoseCycle?>(null)).collectAsStateWithLifecycle()
    val mealBusy by (mealsModel?.busy ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsStateWithLifecycle()
    var mealMenu by remember { mutableStateOf(false) }
    var mealEditor by rememberSaveable { mutableStateOf<String?>(null) }
    val glucoseUnit = GlucoseUnit.MMOL
    val pressureBusy by pressure.busy.collectAsStateWithLifecycle()
    val measurementBusy by model.busy.collectAsStateWithLifecycle()
    val pressureError by pressure.error.collectAsStateWithLifecycle()
    val measurementError by model.error.collectAsStateWithLifecycle()
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) { currentDate = LocalDate.now(); onPauseOrDispose {} }
    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now()
            delay(java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1))
            currentDate = LocalDate.now()
        }
    }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorType by rememberSaveable { mutableStateOf(JournalType.PRESSURE.name) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var initial by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var period by rememberSaveable { mutableStateOf("7") }
    var from by rememberSaveable { mutableStateOf(currentDate.toString()) }
    var until by rememberSaveable { mutableStateOf(currentDate.toString()) }
    var context by rememberSaveable { mutableStateOf<String?>(null) }
    var food by rememberSaveable { mutableStateOf<String?>(null) }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var analytics by rememberSaveable { mutableStateOf(false) }

    fun clearError() { pressure.error.value = null; model.error.value = null }
    fun open(entry: JournalEntry?, kind: JournalType) {
        clearError()
        editorType = kind.name
        editing = entry != null
        editorId = entry?.id ?: UUID.randomUUID().toString()
        val openedAt = System.currentTimeMillis()
        val now = PressureDraft.format(openedAt)
        initial = when (entry) {
            is JournalEntry.Meal -> emptyList()
            is JournalEntry.Pressure -> PressureDraft.from(entry.record).let {
                listOf(it.systolic, it.diastolic, it.pulse, it.time, it.context.orEmpty(), it.comment)
            }
            is JournalEntry.Weight -> entry.record.let {
                listOf(decimalText(BigDecimal.valueOf(it.tenthsKg.toLong(), 1)), PressureDraft.format(it.measuredAt),
                    it.context.orEmpty(), glucoseUnit.name, FoodContext.NONE.name, "", it.comment)
            }
            is JournalEntry.Glucose -> entry.record.let {
                listOf(decimalText(BigDecimal.valueOf(it.hundredths.toLong(), 2)), PressureDraft.format(it.measuredAt),
                    "", it.unit, it.foodContext, it.tag.orEmpty(), it.comment, it.meal.orEmpty(), it.mealEventId.orEmpty())
            }
            null -> if (kind == JournalType.PRESSURE) listOf("", "", "", now, "", "", openedAt.toString())
                else listOf("", now, "", glucoseUnit.name, FoodContext.NONE.name, "", "", "", "", openedAt.toString())
        }
    }
    val all = (journal(pressures.orEmpty(), weights.orEmpty(), glucose.orEmpty()) + meals.map(JournalEntry::Meal))
        .sortedWith(compareByDescending<JournalEntry> { it.at }.thenByDescending { it.createdAt }.thenBy { it.key })
    val listState = rememberLazyListState()
    var knownKeys by remember { mutableStateOf<Set<String>?>(null) }
    val keys = all.map { it.key }.toSet()
    LaunchedEffect(keys, pressures != null && weights != null && glucose != null) {
        if (pressures != null && weights != null && glucose != null) {
            val previous = knownKeys
            knownKeys = keys
            if (previous != null && keys.any { it !in previous }) listState.scrollToItem(0)
        }
    }
    val filter = HistoryFilter(
        type?.let(JournalType::valueOf),
        when (period) { "all" -> null; "custom" -> LocalDate.parse(from); else -> currentDate.minusDays(period.toLong() - 1) },
        when (period) { "all" -> null; "custom" -> LocalDate.parse(until); else -> currentDate },
        context, food?.let(FoodContext::valueOf), tag?.let(GlucoseTag::valueOf),
    )
    EmptyHome(recordActions = {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JournalType.entries.filter { it != JournalType.MEAL || mealsModel != null }.forEach { item ->
                Box {
                    RecordAction(item) { if (item == JournalType.MEAL) mealMenu = true else open(null, item) }
                    if (item == JournalType.MEAL) DropdownMenu(expanded = mealMenu, onDismissRequest = { mealMenu = false }) {
                        MealType.entries.forEach { meal ->
                            DropdownMenuItem(text = { Text(meal.label) }, enabled = !mealBusy,
                                onClick = { mealMenu = false; mealsModel?.start(meal) })
                        }
                    }
                }
            }
        }
    }, settingsContent = {
        if (mealsModel != null) dev.dadyarri.medtracker.reminders.ReminderSettingsScreen {
            var showCycle by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { showCycle = !showCycle }) { Text("Цикл контроля глюкозы") }
            if (showCycle) dev.dadyarri.medtracker.meals.CycleSettings(cycle, mealBusy, mealsModel::toggleCycle, mealsModel::setCycle)
        }
    }) { today ->
        val visible = if (today) HistoryFilter(start = currentDate, end = currentDate).apply(all) else filter.apply(all)
        val loaded = pressures != null && weights != null && glucose != null
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), state = listState,
            contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (today && cycle?.active == true) item("glucose_plan") {
                val tasks = dev.dadyarri.medtracker.meals.GlucoseCyclePlan.tasks(cycle!!, currentDate, meals, ZoneId.systemDefault())
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Контроль глюкозы · день ${cycle!!.day(currentDate)}", style = MaterialTheme.typography.titleMedium)
                    tasks.forEach { task ->
                        Text("${if (task.food == "FASTING") "Натощак" else if (task.food == "BEFORE") "До еды" else "После еды"} · ${PressureDraft.format(task.at).substringAfter(' ')}")
                    }
                }
            }
            if (!today) {
                item("filters") {
                    HistoryControls(filter, period, currentDate, { updated, selection ->
                        type = updated.type?.name; context = updated.context; food = updated.food?.name; tag = updated.tag?.name
                        period = selection
                        updated.start?.let { from = it.toString() }
                        updated.end?.let { until = it.toString() }
                    })
                }
                item("analytics_toggle") {
                    TextButton(onClick = { analytics = !analytics }) { Text(if (analytics) "Скрыть графики" else "Графики и статистика") }
                }
                if (analytics && visible.isNotEmpty()) item("analytics") { Analytics(visible, glucoseUnit) }
            }
            if (editorId == null) {
                (pressureError ?: measurementError)?.let { message -> item("error") { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
            when {
                !loaded -> item("loading") { CircularProgressIndicator() }
                visible.isEmpty() -> item("empty") { Text(if (today) "Сегодня измерений нет" else "Нет измерений за выбранный период") }
                else -> visible.groupBy { Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate() }.forEach { (date, records) ->
                    if (!today) item("date_$date") {
                        Text(date.format(dateFormat), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 12.dp).semantics { heading() })
                    }
                    items(records, key = { it.key }, contentType = { it.type }) { entry ->
                        when (entry) {
                            is JournalEntry.Meal -> dev.dadyarri.medtracker.ui.MeasurementCardFrame(
                                "Еда: ${MealType.valueOf(entry.record.type).label}", entry.at, emptyList(), { mealEditor = entry.id }) {
                                Text(MealType.valueOf(entry.record.type).label, style = MaterialTheme.typography.displaySmall)
                            }
                            is JournalEntry.Pressure -> PressureHistoryCard(entry.record) { open(entry, entry.type) }
                            else -> MeasurementCard(entry, glucoseUnit) { open(entry, entry.type) }
                        }
                    }
                }
            }
        }
    }
    val selectedMeal = meals.find { it.id == mealEditor }
    if (selectedMeal != null && mealsModel != null) ModalBottomSheet(onDismissRequest = { if (!mealBusy) mealEditor = null }) {
        dev.dadyarri.medtracker.meals.MealEditor(selectedMeal, mealBusy,
            { mealsModel.save(it, true) { mealEditor = null } }, { mealsModel.delete(selectedMeal.id) { mealEditor = null } })
    }
    val id = editorId
    if (id != null) {
        val isPressure = editorType == JournalType.PRESSURE.name
        val busy = if (isPressure) pressureBusy else measurementBusy
        val close = { editorId = null; clearError() }
        ModalBottomSheet(onDismissRequest = { if (!busy) close() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
            sheetGesturesEnabled = !busy, properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy)) {
            if (isPressure) PressureEditor(id, initial, editing, busy, pressureError,
                onSave = { pressure.save(id, editing, it, close) }, onDelete = { pressure.delete(id, close) }, onClose = close)
            else {
                val draft = MeasurementDraft(
                    if (editorType == JournalType.WEIGHT.name) MeasurementType.WEIGHT else MeasurementType.GLUCOSE,
                    initial[0], initial[1], initial[2].ifEmpty { null }, GlucoseUnit.valueOf(initial[3]),
                    FoodContext.valueOf(initial[4]), initial[5].ifEmpty { null }?.let(GlucoseTag::valueOf), initial[6],
                    initial.getOrNull(7)?.ifEmpty { null }?.let(MealType::valueOf),
                    initial.getOrNull(8)?.ifEmpty { null },
                    initial.getOrNull(9)?.toLongOrNull(),
                )
                MeasurementEditor(id, draft, editing, busy, measurementError,
                    onSave = { model.save(id, editing, it, close) },
                    onDelete = { model.delete(id, draft.type, close) }, onClose = close, meals = meals)
            }
        }
    }
}

internal val JournalType.icon: Int get() = when (this) {
    JournalType.MEAL -> R.drawable.ic_context_fasting
    JournalType.PRESSURE -> R.drawable.ic_blood_pressure
    JournalType.WEIGHT -> R.drawable.ic_weight
    JournalType.GLUCOSE -> R.drawable.ic_glucose
}
@Composable
private fun RecordAction(type: JournalType, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.widthIn(min = 88.dp).heightIn(min = 80.dp).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            Icon(painterResource(type.icon), null, Modifier.size(28.dp))
            Text(type.label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
@Composable
private fun MeasurementCard(entry: JournalEntry, unit: GlucoseUnit, onClick: () -> Unit) {
    val value: String
    var contextLabel: String? = null
    val badges: List<Pair<Int, String>>
    when (entry) {
        is JournalEntry.Weight -> {
            value = decimalText(BigDecimal.valueOf(entry.record.tenthsKg.toLong(), 1))
            badges = PressureContext.entries.find { it.name == entry.record.context }?.let { listOf(it.iconResource to it.label) }.orEmpty()
        }
        is JournalEntry.Glucose -> {
            value = decimalText(entry.record.valueIn(unit).setScale(2, java.math.RoundingMode.HALF_UP))
            val food = FoodContext.valueOf(entry.record.foodContext)
            badges = emptyList()
            val meal = entry.record.meal?.let(MealType::valueOf)
            val ending = when (meal) {
                MealType.BREAKFAST -> "завтрака"
                MealType.LUNCH -> "обеда"
                MealType.DINNER -> "ужина"
                MealType.SNACK -> "перекуса"
                null -> "еды"
            }
            contextLabel = when (food) {
                FoodContext.BEFORE -> "До $ending"
                FoodContext.AFTER -> "После $ending"
                else -> food.label
            }
        }
        else -> return
    }
    dev.dadyarri.medtracker.ui.MeasurementCardFrame("${entry.type.label}: $value", entry.at, badges, onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(contextLabel?.let { "${entry.type.label} · $it" } ?: entry.type.label,
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.displaySmall)
        }
    }
}
