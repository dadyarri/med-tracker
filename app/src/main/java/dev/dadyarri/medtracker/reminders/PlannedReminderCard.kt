package dev.dadyarri.medtracker.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.measurements.JournalEntry
import java.time.*
import java.time.format.DateTimeFormatter

internal data class TodayItem(val record: JournalEntry? = null, val planned: ReminderOccurrence? = null) {
    val at: Long get() = record?.at ?: planned!!.dueAt
    val key: String get() = record?.key ?: "plan/${planned!!.key}"
}
internal fun todayTimeline(records: List<JournalEntry>, plans: List<ReminderOccurrence>, date: LocalDate,
                           zone: ZoneId = ZoneId.systemDefault()): List<TodayItem> =
    (records.filter { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == date }.map { TodayItem(record = it) } +
        plans.filter { it.canRecord && it.entryType != null &&
            Instant.ofEpochMilli(it.dueAt).atZone(zone).toLocalDate() == date }.map { TodayItem(planned = it) })
        .sortedWith(compareByDescending<TodayItem> { it.at }.thenBy { it.key })

@Composable
internal fun PlannedReminderCard(row: ReminderOccurrence, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(onClick = onClick, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = textColor,
        modifier = Modifier.fillMaxWidth().drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val stroke = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            onDrawWithContent { drawContent(); drawOutline(outline, outlineColor, style = stroke) }
        }) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(dev.dadyarri.medtracker.R.drawable.ic_clock), null, Modifier.size(16.dp))
                Text(Instant.ofEpochMilli(row.dueAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm")),
                    style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                Text("Запланировано", style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(row.icon), null, Modifier.size(28.dp))
                Text(row.title, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
