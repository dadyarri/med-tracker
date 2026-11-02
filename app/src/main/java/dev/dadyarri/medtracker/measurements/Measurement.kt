package dev.dadyarri.medtracker.measurements

import androidx.room.*
import dev.dadyarri.medtracker.pressure.PressureContext
import dev.dadyarri.medtracker.pressure.PressureDraft
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.math.RoundingMode

enum class MeasurementType(val label: String) { WEIGHT("Вес"), GLUCOSE("Глюкоза") }
enum class GlucoseUnit(val label: String) { MMOL("ммоль/л"), MG("мг/дл") }
enum class FoodContext(val label: String) {
    FASTING("Натощак"), BEFORE("До еды"), AFTER("После еды"), NONE("Без привязки")
}
enum class MealType(val label: String) { BREAKFAST("Завтрак"), LUNCH("Обед"), DINNER("Ужин"), SNACK("Перекус") }
enum class GlucoseTag(val label: String) { BEDTIME("Перед сном"), UNPLANNED("Внепланово") }

// Weight is stored in tenths of kg. Glucose keeps hundredths of the original unit;
// changing the display unit never rewrites or rounds the stored measurement.
@Entity(tableName = "weight", indices = [Index("measuredAt")])
data class WeightRecord(
    @PrimaryKey val id: String, val tenthsKg: Int, val context: String?, val comment: String,
    val measuredAt: Long, val createdAt: Long, val updatedAt: Long,
)
@Entity(tableName = "glucose", indices = [Index("measuredAt")])
data class GlucoseRecord(
    @PrimaryKey val id: String, val hundredths: Int, val unit: String,
    val foodContext: String, val tag: String?, val comment: String,
    val measuredAt: Long, val createdAt: Long, val updatedAt: Long,
    val meal: String? = null,
    val mealEventId: String? = null,
) {
    fun valueIn(target: GlucoseUnit): BigDecimal {
        val value = BigDecimal.valueOf(hundredths.toLong(), 2)
        if (unit == target.name) return value
        return if (target == GlucoseUnit.MG) value.multiply(BigDecimal("18"))
        else value.divide(BigDecimal("18"), 6, RoundingMode.HALF_UP)
    }
}
@Dao
interface MeasurementDao {
    @Query("SELECT * FROM weight ORDER BY measuredAt DESC, createdAt DESC, id")
    fun weights(): Flow<List<WeightRecord>>
    @Query("SELECT * FROM glucose ORDER BY measuredAt DESC, createdAt DESC, id")
    fun glucose(): Flow<List<GlucoseRecord>>
    @Query("SELECT * FROM weight WHERE id = :id") suspend fun weight(id: String): WeightRecord?
    @Query("SELECT * FROM glucose WHERE id = :id") suspend fun glucose(id: String): GlucoseRecord?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(record: WeightRecord): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(record: GlucoseRecord): Long
    @Update suspend fun update(record: WeightRecord): Int
    @Update suspend fun update(record: GlucoseRecord): Int
    @Query("DELETE FROM weight WHERE id = :id") suspend fun deleteWeight(id: String): Int
    @Query("DELETE FROM glucose WHERE id = :id") suspend fun deleteGlucose(id: String): Int
}

fun decimalText(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().replace('.', ',')
fun scaledValue(text: String, scale: Int): Int? = try {
    if (!text.matches(Regex("[0-9]+([.,][0-9]{1,$scale})?"))) null
    else text.replace(',', '.').toBigDecimal().movePointRight(scale).intValueExact().takeIf { it > 0 }
} catch (_: ArithmeticException) { null }

data class MeasurementDraft(
    val type: MeasurementType, val value: String = "", val time: String,
    val context: String? = null, val unit: GlucoseUnit = GlucoseUnit.MMOL,
    val food: FoodContext = FoodContext.NONE, val tag: GlucoseTag? = null, val comment: String = "",
    val meal: MealType? = null,
    val mealEventId: String? = null,
    val originalAt: Long? = null,
) {
    fun scaled(): Int? = scaledValue(value, if (type == MeasurementType.WEIGHT) 1 else 2)
    fun instant() = PressureDraft(time = time, originalAt = originalAt).instant()
    fun error(): String? = when {
        scaled() == null -> if (type == MeasurementType.WEIGHT) "Введите положительный вес с точностью до 0,1 кг."
            else "Введите положительную глюкозу: не более двух знаков после запятой."
        instant() == null -> "Выберите дату и время."
        context != null && PressureContext.entries.none { it.name == context } -> "Выберите контекст."
        else -> null
    }
    // Broad typo warnings, without diagnostic interpretation or blocking possible values.
    fun unusual(): Boolean {
        val amount = scaled() ?: return false
        return if (type == MeasurementType.WEIGHT) amount !in 200..4000
        else if (unit == GlucoseUnit.MMOL) amount !in 100..4000 else amount !in 1800..72000
    }
}
