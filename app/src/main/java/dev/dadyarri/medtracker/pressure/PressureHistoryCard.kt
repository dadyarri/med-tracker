package dev.dadyarri.medtracker.pressure

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.R
import java.time.Instant
import java.time.LocalDate
import androidx.compose.ui.semantics.heading
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val PressureContext.iconResource: Int
    get() = when (this) {
        PressureContext.MORNING -> R.drawable.ic_context_morning
        PressureContext.DAY -> R.drawable.ic_context_day
        PressureContext.EVENING -> R.drawable.ic_context_evening
        PressureContext.NIGHT -> R.drawable.ic_context_night
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PressureHistoryCard(record: PressureRecord, onClick: () -> Unit) {
    val measured = Instant.ofEpochMilli(record.measuredAt).atZone(ZoneId.systemDefault())
    val context = PressureContext.entries.find { it.name == record.context }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Давление: ${record.systolic} на ${record.diastolic}" },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_clock), contentDescription = null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(measured.format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (context != null) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(context.label) } }, state = rememberTooltipState(),
                    ) {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(painterResource(context.iconResource), contentDescription = context.label,
                                modifier = Modifier.padding(10.dp).size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HistoryValue("Верхнее", record.systolic, Modifier.weight(1f))
                HistoryValue("Нижнее", record.diastolic, Modifier.weight(1f))
            }
            record.pulse?.let { pulse ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Favorite, contentDescription = null, Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Text("Пульс", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(pulse.toString(), style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            if (record.comment.isNotBlank()) Text(record.comment, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PressureRecordList(records: List<PressureRecord>, groupByDate: Boolean, onOpen: (PressureRecord) -> Unit) {
    val groups = records.groupBy { Instant.ofEpochMilli(it.measuredAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
        groups.forEach { (date, rows) ->
            if (groupByDate) item(key = "date_$date", contentType = "date") {
                PressureDateHeader(date)
            }
            items(rows, key = { it.id }, contentType = { "pressure" }) { row ->
                PressureHistoryCard(row) { onOpen(row) }
            }
        }
    }
}

@Composable
private fun PressureDateHeader(date: LocalDate) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Default.DateRange, contentDescription = null, Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(date.format(DateTimeFormatter.ofPattern("dd.MM.uuuu")), style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryValue(label: String, value: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.toString(), style = MaterialTheme.typography.displaySmall)
    }
}
