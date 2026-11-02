package dev.dadyarri.medtracker.measurements

import dev.dadyarri.medtracker.pressure.PressureRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId

enum class JournalType(val label: String) { PRESSURE("Давление"), WEIGHT("Вес"), GLUCOSE("Глюкоза") }
sealed interface JournalEntry {
    val id: String
    val at: Long
    val createdAt: Long
    val type: JournalType
    val key get() = "${type.name}_$id"
    data class Pressure(val record: PressureRecord) : JournalEntry {
        override val id get() = record.id
        override val at get() = record.measuredAt
        override val createdAt get() = record.createdAt
        override val type get() = JournalType.PRESSURE
    }
    data class Weight(val record: WeightRecord) : JournalEntry {
        override val id get() = record.id
        override val at get() = record.measuredAt
        override val createdAt get() = record.createdAt
        override val type get() = JournalType.WEIGHT
    }
    data class Glucose(val record: GlucoseRecord) : JournalEntry {
        override val id get() = record.id
        override val at get() = record.measuredAt
        override val createdAt get() = record.createdAt
        override val type get() = JournalType.GLUCOSE
    }
}
fun journal(pressure: List<PressureRecord>, weights: List<WeightRecord>, glucose: List<GlucoseRecord>): List<JournalEntry> =
    (pressure.map(JournalEntry::Pressure) + weights.map(JournalEntry::Weight) + glucose.map(JournalEntry::Glucose))
        .sortedWith(compareByDescending<JournalEntry> { it.at }.thenByDescending { it.createdAt }.thenBy { it.key })

data class HistoryFilter(
    val type: JournalType? = null, val start: LocalDate? = null, val end: LocalDate? = null,
    val context: String? = null, val food: FoodContext? = null, val tag: GlucoseTag? = null,
) {
    fun apply(records: List<JournalEntry>, zone: ZoneId = ZoneId.systemDefault()): List<JournalEntry> {
        val from = start?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        val until = end?.plusDays(1)?.atStartOfDay(zone)?.toInstant()?.toEpochMilli()
        return records.filter { entry ->
            (type == null || entry.type == type) && (from == null || entry.at >= from) &&
                (until == null || entry.at < until) && when (entry) {
                    is JournalEntry.Pressure -> context == null || entry.record.context == context
                    is JournalEntry.Weight -> context == null || entry.record.context == context
                    is JournalEntry.Glucose -> (food == null || entry.record.foodContext == food.name) &&
                        (tag == null || entry.record.tag == tag.name)
                }
        }
    }
}
data class PlotPoint(val at: Long, val value: BigDecimal, val context: String? = null)
data class MeasurementSeries(val title: String, val unit: String, val points: List<PlotPoint>)
data class Summary(val count: Int, val minimum: BigDecimal, val maximum: BigDecimal, val average: BigDecimal)
fun MeasurementSeries.summary(): Summary? = if (points.isEmpty()) null else {
    val values = points.map { it.value }
    Summary(values.size, values.minOrNull()!!, values.maxOrNull()!!,
        values.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(values.size), 2, RoundingMode.HALF_UP))
}
fun measurementSeries(entries: List<JournalEntry>, unit: GlucoseUnit): List<MeasurementSeries> {
    val sorted = entries.sortedBy { it.at }
    val pressure = sorted.filterIsInstance<JournalEntry.Pressure>()
    val weights = sorted.filterIsInstance<JournalEntry.Weight>()
    val glucose = sorted.filterIsInstance<JournalEntry.Glucose>()
    return buildList {
        if (pressure.isNotEmpty()) {
            add(MeasurementSeries("Верхнее", "мм рт. ст.", pressure.map { PlotPoint(it.at, it.record.systolic.toBigDecimal()) }))
            add(MeasurementSeries("Нижнее", "мм рт. ст.", pressure.map { PlotPoint(it.at, it.record.diastolic.toBigDecimal()) }))
            add(MeasurementSeries("Пульс", "уд/мин", pressure.mapNotNull { entry ->
                entry.record.pulse?.let { PlotPoint(entry.at, it.toBigDecimal()) }
            }))
        }
        if (weights.isNotEmpty()) add(MeasurementSeries("Вес", "кг", weights.map {
            PlotPoint(it.at, BigDecimal.valueOf(it.record.tenthsKg.toLong(), 1))
        }))
        if (glucose.isNotEmpty()) add(MeasurementSeries("Глюкоза", unit.label, glucose.map {
            PlotPoint(it.at, it.record.valueIn(unit), it.record.foodContext)
        }))
    }.filter { it.points.isNotEmpty() }
}
