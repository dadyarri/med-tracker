package dev.dadyarri.medtracker.measurements

import dev.dadyarri.medtracker.pressure.PressureRecord
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

class MeasurementTest {
    @Test fun decimalInputIsExactAndRejectsPrecisionLossAndOverflow() {
        assertEquals(723, scaledValue("72,3", 1))
        assertEquals(567, scaledValue("5.67", 2))
        listOf("", "0", "-1", "NaN", "1e3", "1.01", "999999999999").forEach { assertNull(it, scaledValue(it, 1)) }
        val glucose = GlucoseRecord("g", 567, "MMOL", "FASTING", null, "", 1, 1, 1)
        assertEquals(0, BigDecimal("102.06").compareTo(glucose.valueIn(GlucoseUnit.MG)))
        assertEquals(567, glucose.hundredths)
        assertEquals(0, BigDecimal("5").compareTo(glucose.copy(hundredths = 9000, unit = "MG").valueIn(GlucoseUnit.MMOL)))
    }
    @Test fun filterUsesInclusiveLocalDaysAcrossDstAndStatsUseOnlyMatchingFacts() {
        val zone = ZoneId.of("Europe/Berlin")
        val day = LocalDate.of(2026, 10, 25) // 25-hour day
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        fun g(id: String, at: Long, food: String, value: Int, unit: String = "MMOL") =
            JournalEntry.Glucose(GlucoseRecord(id, value, unit, food, "UNPLANNED", "", at, at, at))
        val entries = listOf(g("before", start - 1, "FASTING", 1000), g("one", start, "FASTING", 500),
            g("two", end - 1, "FASTING", 12600, "MG"), g("after", end, "FASTING", 2000),
            g("food", start + 1, "AFTER", 1500))
        val selected = HistoryFilter(JournalType.GLUCOSE, day, day, food = FoodContext.FASTING, tag = GlucoseTag.UNPLANNED).apply(entries, zone)
        assertEquals(listOf("one", "two"), selected.map { it.id })
        val series = measurementSeries(selected, GlucoseUnit.MMOL).single()
        assertEquals(2, series.points.size)
        val stats = series.summary()!!
        assertEquals(2, stats.count)
        assertEquals(0, BigDecimal("5").compareTo(stats.minimum))
        assertEquals(0, BigDecimal("7").compareTo(stats.maximum))
        assertEquals(0, BigDecimal("6").compareTo(stats.average))
        assertTrue(HistoryFilter(food = FoodContext.BEFORE).apply(entries, zone).isEmpty())
    }
    @Test fun pressureAndPulseAreIndependentAndWeightHasDecimalStatistics() {
        val entries = journal(listOf(PressureRecord("p1", 120, 80, null, "MORNING", "", 1, 1, 1),
            PressureRecord("p2", 130, 85, 70, "NIGHT", "", 2, 2, 2)),
            listOf(WeightRecord("w", 723, "MORNING", "", 3, 3, 3)), emptyList())
        assertEquals(listOf("w", "p2", "p1"), entries.map { it.id })
        val series = measurementSeries(entries, GlucoseUnit.MMOL)
        assertEquals(1, series.single { it.title == "Пульс" }.points.size)
        assertEquals(2, series.single { it.title == "Верхнее" }.points.size)
        assertEquals(0, BigDecimal("72.3").compareTo(series.single { it.title == "Вес" }.summary()!!.average))
        assertEquals(2, HistoryFilter(context = "MORNING").apply(entries).size)
        assertTrue(measurementSeries(emptyList(), GlucoseUnit.MMOL).isEmpty())
    }
}
