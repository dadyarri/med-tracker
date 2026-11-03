package dev.dadyarri.medtracker.medications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.dadyarri.medtracker.R
import dev.dadyarri.medtracker.pressure.PressureDraft
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationScreen(model: MedicationViewModel) {
    val data by model.data.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val meals by model.meals.collectAsStateWithLifecycle()
    val settings by model.mealSettings.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var archive by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var editorId by rememberSaveable { mutableStateOf("") }
    var regimenId by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) { now = System.currentTimeMillis(); onPauseOrDispose {} }
    val content = data ?: run { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    val medication = content.medications.find { it.id == selected }
    fun close() { sheet = null; model.error.value = null; now = System.currentTimeMillis() }
    fun open(kind: String) { model.error.value = null; sheet = kind }
    fun intake(drug: Medication, regimen: MedicationRegimen? = null, version: RegimenVersion? = null, edit: Boolean = false) {
        selected = drug.id
        now = System.currentTimeMillis()
        editorId = medicationId(); regimenId = regimen?.id
        if (edit) open("intake")
        else model.intake(MedicationIntake(editorId, drug.id, regimen?.id, version?.id,
            version?.dose ?: drug.usualDose, version?.unit ?: drug.unit,
            now, 0, 0), false) { now = System.currentTimeMillis() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!archive, { archive = false }, label = { Text("Активные") })
            FilterChip(archive, { archive = true }, label = { Text("Архив") })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sheet == null) error?.let { item("error") { Text(it, color = MaterialTheme.colorScheme.error) } }
            val visible = content.medications.filter { it.archived == archive }
            if (visible.isEmpty()) item("empty") { Text(if (archive) "Архив пуст" else "Нет препаратов",
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(visible, key = { it.id }) { drug ->
                SwipeableMedicationCard(drug, !busy,
                    onClick = { if (!drug.archived) intake(drug, edit = true) },
                    onEdit = { editorId = drug.id; open("medication") },
                    onArchive = { editorId = drug.id; open("archive") },
                    onRegimens = { selected = drug.id; open("regimens") })
            }
        }
        Button(onClick = { editorId = medicationId(); open("medication") },
            enabled = !busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) { Text("Добавить препарат") }
    }
    if (sheet in listOf("medication", "regimens", "regimen", "revise", "repeat", "intake")) {
        ModalBottomSheet(onDismissRequest = { if (!busy) close() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            sheetGesturesEnabled = !busy, properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy)) {
            key(sheet, editorId, regimenId, selected) {
                when (sheet) {
                    "medication" -> MedicationEditor(content.medications.find { it.id == editorId } ?: Medication(id = editorId),
                        busy, error, { value -> model.save(value, ::close) }, ::close)
                    "regimens" -> if (medication != null) {
                        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item("title") {
                                Text("Назначения", style = MaterialTheme.typography.headlineSmall)
                                Text(medication.name, style = MaterialTheme.typography.titleMedium)
                            }
                            if (!medication.archived) item("add") {
                                FilledTonalButton(onClick = { regimenId = null; open("regimen") }, enabled = !busy) {
                                    Icon(painterResource(R.drawable.ic_calendar), null, Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp)); Text("Добавить назначение")
                                }
                            }
                            val regimens = content.regimens.filter { it.medicationId == medication.id }.sortedByDescending { it.startsAt }
                            if (regimens.isEmpty()) item("empty") { Text("Нет назначений", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(regimens, key = { it.id }) { regimen ->
                                val version = content.versions.at(regimen.id, now)
                                    ?: content.versions.filter { it.regimenId == regimen.id }.minByOrNull { it.effectiveAt }
                                if (version != null) {
                                    val finished = regimen.endsAt?.let { it <= now } == true || regimen.completedAt?.let { it <= now } == true
                                    val next = MedicationSchedule.tasks(regimen, content.versions, content.intakes, meals, settings,
                                        Instant.ofEpochMilli(now), Instant.ofEpochMilli(now).plusSeconds(8 * 86400L), ZoneId.systemDefault()).firstOrNull()
                                    RegimenCard(regimen, version, next, content.versions.filter { it.regimenId == regimen.id },
                                        finished, busy || medication.archived,
                                        onTake = { intake(medication, regimen, version) },
                                        onEditIntake = { intake(medication, regimen, version, true) },
                                        onRevise = { regimenId = regimen.id; open("revise") },
                                        onRepeat = { regimenId = regimen.id; open("repeat") },
                                        onFinish = { regimenId = regimen.id; open("finish") })
                                }
                            }
                            error?.let { item("error") { Text(it, color = MaterialTheme.colorScheme.error) } }
                        }
                    }
                    "regimen", "revise", "repeat" -> if (medication != null) {
                        val old = content.regimens.find { it.id == regimenId }
                        val version = old?.let {
                            val at = minOf(now, it.completedAt ?: Long.MAX_VALUE, it.endsAt ?: Long.MAX_VALUE)
                            content.versions.at(it.id, maxOf(it.startsAt, at - 1))
                        }
                        RegimenEditor(medication, old.takeIf { sheet == "revise" }, version, busy, error,
                            repeatFrom = old.takeIf { sheet == "repeat" }, onSave = { regimen, snapshot ->
                                if (sheet == "revise") model.revise(snapshot, ::close) else model.prescribe(regimen, snapshot, ::close)
                            }, onClose = ::close)
                    }
                    "intake" -> if (medication != null) {
                        val version = regimenId?.let { content.versions.at(it, now) }
                        val value = MedicationIntake(editorId, medication.id, regimenId, version?.id,
                            version?.dose ?: medication.usualDose, version?.unit ?: medication.unit, now, 0, 0)
                        IntakeEditor(medication, value, false, content.regimens.filter { it.medicationId == medication.id },
                            busy, error, { model.intake(it, false, ::close) }, {}, ::close)
                    }
                }
            }
        }
    }
    val archiveTarget = content.medications.find { it.id == editorId }
    if (sheet == "archive" && archiveTarget != null) AlertDialog(onDismissRequest = ::close,
        title = { Text(if (archiveTarget.archived) "Вернуть препарат?" else "Убрать в архив?") },
        text = { Text(if (archiveTarget.archived) "Завершённые назначения останутся в истории." else "Открытые назначения будут завершены. История сохранится.") },
        confirmButton = { TextButton(onClick = { model.archive(archiveTarget, ::close) }, enabled = !busy) { Text("Продолжить") } },
        dismissButton = { TextButton(onClick = ::close) { Text("Отмена") } })
    if (sheet == "finish") AlertDialog(onDismissRequest = ::close, title = { Text("Завершить назначение?") },
        confirmButton = { TextButton(onClick = { regimenId?.let { model.finish(it, ::close) } }, enabled = !busy) { Text("Завершить") } },
        dismissButton = { TextButton(onClick = ::close) { Text("Отмена") } })
}

@Composable
internal fun MedicationCard(medication: Medication, onClick: () -> Unit) {
    Card(onClick = onClick, shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            MedicationVisual(medication, Modifier.size(64.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(medication.name, style = MaterialTheme.typography.titleLarge)
                if (medication.strength.isNotBlank()) Text(medication.strength, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${medication.usualDose.replace('.', ',')} ${medication.unit}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RegimenCard(regimen: MedicationRegimen, version: RegimenVersion, next: MedicationTask?, history: List<RegimenVersion>,
                        finished: Boolean, busy: Boolean, onTake: () -> Unit, onEditIntake: () -> Unit,
                        onRevise: () -> Unit, onRepeat: () -> Unit, onFinish: () -> Unit) {
    var showVersions by rememberSaveable(regimen.id) { mutableStateOf(false) }
    Card(shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (finished) "Завершено" else RegimenKind.valueOf(regimen.kind).label, style = MaterialTheme.typography.titleMedium)
            Text(version.dose.replace('.', ','), style = MaterialTheme.typography.displaySmall)
            Text(version.unit, style = MaterialTheme.typography.labelMedium)
            Text(when (version.mode) {
                "HOURS" -> "Каждые ${version.everyHours} ч"
                "DAYS" -> "Каждые ${version.everyDays} дн."
                else -> ScheduleMode.valueOf(version.mode).label
            }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (next != null) Text(PressureDraft.format(next.at), style = MaterialTheme.typography.labelLarge)
            if (version.food != "NONE") Text(MedicationFood.valueOf(version.food).label, style = MaterialTheme.typography.labelMedium)
            if (history.size > 1) TextButton(onClick = { showVersions = !showVersions }) { Text("Версии схемы") }
            if (showVersions) history.sortedByDescending { it.effectiveAt }.forEach { item ->
                Text("${PressureDraft.format(item.effectiveAt)} · ${item.dose.replace('.', ',')} ${item.unit} · ${ScheduleMode.valueOf(item.mode).label}",
                    style = MaterialTheme.typography.labelMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!finished) {
                    Button(onClick = onTake, enabled = !busy && regimen.startsAt <= System.currentTimeMillis()) { Text("Принял") }
                    OutlinedButton(onClick = onEditIntake, enabled = !busy) { Text("Изменить") }
                    TextButton(onClick = onRevise, enabled = !busy) { Text("Схема") }
                    TextButton(onClick = onFinish, enabled = !busy) { Text("Завершить") }
                } else TextButton(onClick = onRepeat, enabled = !busy) { Text("Повторить курс") }
            }
        }
    }
}
