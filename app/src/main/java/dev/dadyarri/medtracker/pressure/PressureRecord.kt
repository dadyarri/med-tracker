package dev.dadyarri.medtracker.pressure

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

enum class PressureContext(val label: String) {
    MORNING("Утро"), DAY("День"), EVENING("Вечер"), NIGHT("Ночь");

    companion object {
        fun forHour(hour: Int): PressureContext {
            require(hour in 0..23)
            return when (hour) {
                in 6..11 -> MORNING
                in 12..17 -> DAY
                in 18..23 -> EVENING
                else -> NIGHT
            }
        }
    }
}

@Entity(tableName = "blood_pressure", indices = [Index("measuredAt")])
data class PressureRecord(
    @PrimaryKey val id: String,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val context: String?,
    val comment: String,
    val measuredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface PressureDao {
    @Query("SELECT * FROM blood_pressure ORDER BY measuredAt DESC, createdAt DESC, id")
    fun observe(): Flow<List<PressureRecord>>
    @Query("SELECT * FROM blood_pressure WHERE id = :id")
    suspend fun get(id: String): PressureRecord?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: PressureRecord): Long
    @Update suspend fun update(record: PressureRecord): Int
    @Query("DELETE FROM blood_pressure WHERE id = :id")
    suspend fun delete(id: String): Int
}

data class PressureDraft(
    val systolic: String = "",
    val diastolic: String = "",
    val pulse: String = "",
    val time: String,
    val context: String? = null,
    val comment: String = "",
    val originalAt: Long? = null,
) {
    fun error(): String? = when {
        systolic.toIntOrNull()?.let { it > 0 } != true -> "Введите положительное целое верхнее давление."
        diastolic.toIntOrNull()?.let { it > 0 } != true -> "Введите положительное целое нижнее давление."
        systolic.toInt() <= diastolic.toInt() -> "Верхнее давление должно быть больше нижнего."
        pulse.isNotBlank() && pulse.toIntOrNull()?.let { it > 0 } != true -> "Пульс — положительное целое число или пустое поле."
        instant() == null -> "Дата и время: дд.мм.гггг чч:мм."
        context != null && PressureContext.entries.none { it.name == context } -> "Выберите контекст из списка."
        else -> null
    }
    fun instant(): Long? = originalAt?.takeIf { format(it) == time } ?: parseInstant()
    private fun parseInstant(): Long? = try {
        LocalDateTime.parse(time, TIME_FORMAT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) { null }

    // Input confirmation only, never a diagnosis or a reason to reject a record.
    fun unusual(): Boolean = error() == null &&
        (systolic.toInt() !in 70..250 || diastolic.toInt() !in 40..150 ||
            (pulse.isNotBlank() && pulse.toInt() !in 30..220))

    companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT)
        fun format(at: Long): String = TIME_FORMAT.format(java.time.Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
        fun from(record: PressureRecord) = PressureDraft(
            record.systolic.toString(), record.diastolic.toString(), record.pulse?.toString().orEmpty(),
            format(record.measuredAt), record.context, record.comment,
        )
    }
}
