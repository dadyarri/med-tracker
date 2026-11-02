package dev.dadyarri.medtracker.measurements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dadyarri.medtracker.pressure.PressureContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu")

@Composable
internal fun HistoryControls(filter: HistoryFilter, period: String, today: LocalDate,
    onChange: (HistoryFilter, String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceRow(listOf("Все" to "", *JournalType.entries.map { it.label to it.name }.toTypedArray()),
            filter.type?.name.orEmpty()) { name ->
            onChange(filter.copy(type = JournalType.entries.find { it.name == name }, context = null, food = null, tag = null), period)
        }
        ChoiceRow(listOf("7 дней" to "7", "30 дней" to "30", "90 дней" to "90", "Всё время" to "all", "Период" to "custom"), period) {
            onChange(filter.copy(start = it.toLongOrNull()?.let { days -> today.minusDays(days - 1) }, end = if (it == "all") null else today), it)
        }
        if (period == "custom") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateFilterButton("С", filter.start ?: today, Modifier.weight(1f)) { day ->
                    onChange(filter.copy(start = day, end = (filter.end ?: day).coerceAtLeast(day)), period)
                }
                DateFilterButton("По", filter.end ?: today, Modifier.weight(1f)) { day ->
                    onChange(filter.copy(end = day, start = (filter.start ?: day).coerceAtMost(day)), period)
                }
            }
        }
        if (filter.type == JournalType.PRESSURE || filter.type == JournalType.WEIGHT) {
            ChoiceRow(listOf("Любой контекст" to "", *PressureContext.entries.map { it.label to it.name }.toTypedArray()),
                filter.context.orEmpty()) { onChange(filter.copy(context = it.ifEmpty { null }), period) }
        }
        if (filter.type == JournalType.GLUCOSE) {
            ChoiceRow(listOf("Все контексты" to "", *FoodContext.entries.map { it.label to it.name }.toTypedArray()),
                filter.food?.name.orEmpty()) { name ->
                onChange(filter.copy(food = FoodContext.entries.find { it.name == name }), period)
            }
            ChoiceRow(listOf("Все характеристики" to "", *GlucoseTag.entries.map { it.label to it.name }.toTypedArray()),
                filter.tag?.name.orEmpty()) { name ->
                onChange(filter.copy(tag = GlucoseTag.entries.find { it.name == name }), period)
            }
        }

    }
}
@Composable
private fun ChoiceRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, key) ->
            FilterChip(selected = selected == key, onClick = { onSelect(key) }, label = { Text(label) })
        }
    }
}
@Composable
private fun DateFilterButton(label: String, day: LocalDate, modifier: Modifier, onSelect: (LocalDate) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) { Text("$label ${day.format(dateFormat)}") }
    if (open) dev.dadyarri.medtracker.ui.AppDatePicker(day, { open = false }) {
        onSelect(it); open = false
    }
}

@Composable
internal fun Analytics(entries: List<JournalEntry>, unit: GlucoseUnit) {
    val series = measurementSeries(entries, unit)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Pressure shares a scale; pulse remains a separate chart.
        val groups = series.groupBy { if (it.title == "Верхнее" || it.title == "Нижнее") "Давление" else it.title }
        groups.forEach { (title, values) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    MeasurementPlot(values)
                    values.forEach { item ->
                        val stats = item.summary()!!
                        Text("${item.title}, ${item.unit}", style = MaterialTheme.typography.labelLarge)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            maxItemsInEachRow = 3,
                        ) {
                            listOf("Минимум" to stats.minimum, "Максимум" to stats.maximum, "Среднее" to stats.average)
                                .forEach { (label, value) ->
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.semantics(mergeDescendants = true) {}) {
                                        Text(decimalText(value), style = MaterialTheme.typography.displaySmall)
                                        Text(label, style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                        }
                        Text("Измерений: ${stats.count}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
@Composable
private fun MeasurementPlot(series: List<MeasurementSeries>) {
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSurface)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val points = series.flatMap { it.points }
    val min = points.minOf { it.value.toDouble() }
    val max = points.maxOf { it.value.toDouble() }
    val first = points.minOf { it.at }
    val last = points.maxOf { it.at }
    val contextColors = FoodContext.entries.mapIndexed { index, value -> value.name to colors[index] }.toMap()
    Text("${decimalText(points.minOf { it.value })} — ${decimalText(points.maxOf { it.value })} ${series.first().unit}",
        style = MaterialTheme.typography.labelMedium)
    Canvas(Modifier.fillMaxWidth().height(160.dp).semantics {
        contentDescription = "График: ${series.joinToString { it.title }}. Только фактические измерения: ${points.size}."
    }) {
        val inset = 8.dp.toPx()
        fun point(item: PlotPoint): Offset {
            val x = if (last == first) 0.5 else (item.at - first).toDouble() / (last - first)
            val y = if (max == min) 0.5 else (item.value.toDouble() - min) / (max - min)
            return Offset(inset + (size.width - 2 * inset) * x.toFloat(),
                size.height - inset - (size.height - 2 * inset) * y.toFloat())
        }
        repeat(3) { index ->
            val y = inset + (size.height - 2 * inset) * index / 2
            drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
        }
        series.forEachIndexed { index, item ->
            item.points.zipWithNext().forEach { (left, right) ->
                drawLine(colors[index], point(left), point(right), 2.dp.toPx())
            }
            item.points.forEach { value ->
                drawCircle(value.context?.let(contextColors::get) ?: colors[index], 4.dp.toPx(), point(value))
            }
        }
    }
    fun time(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(time(first), style = MaterialTheme.typography.labelSmall)
        if (last != first) Text(time(last), style = MaterialTheme.typography.labelSmall)
    }
    if (series.any { it.title == "Глюкоза" }) {
        Column {
            FoodContext.entries.forEachIndexed { index, food ->
                Text("● ${food.label}", color = colors[index], style = MaterialTheme.typography.labelMedium)
            }
        }
    } else if (series.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        series.forEachIndexed { index, item -> Text("● ${item.title}", color = colors[index], style = MaterialTheme.typography.labelMedium) }
    }
}
