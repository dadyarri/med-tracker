package dev.dadyarri.medtracker.meals

import androidx.room.*
import dev.dadyarri.medtracker.measurements.MealType
import java.time.*
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meal_event", indices = [Index("startedAt")])
data class MealEvent(
    @PrimaryKey val id: String, val type: String, val startedAt: Long,
    val createdAt: Long, val updatedAt: Long,
)
@Entity(tableName = "glucose_cycle")
data class GlucoseCycle(
    @PrimaryKey val id: Int = 1, val active: Boolean = false, val startDate: String,
    val breakfastMinute: Int = 480, val lunchMinute: Int = 780, val dinnerMinute: Int = 1140,
    val afterMinutes: Int = 120,
    @ColumnInfo(defaultValue = "'BREAKFAST/FASTING/AFTER'") val day1: String = "BREAKFAST/FASTING/AFTER",
    @ColumnInfo(defaultValue = "'LUNCH/BEFORE/AFTER'") val day2: String = "LUNCH/BEFORE/AFTER",
    @ColumnInfo(defaultValue = "'DINNER/BEFORE/AFTER'") val day3: String = "DINNER/BEFORE/AFTER",
) {
    fun day(date: LocalDate): Int? {
        val days = ChronoUnit.DAYS.between(LocalDate.parse(startDate), date)
        return if (!active || days < 0) null else (days % 3).toInt() + 1
    }
    fun withMealTimes(settings: List<dev.dadyarri.medtracker.reminders.ReminderSetting>): GlucoseCycle {
        fun minute(kind: dev.dadyarri.medtracker.reminders.ReminderKind) =
            settings.find { it.kind == kind }?.minute ?: kind.defaultMinute
        return copy(
            breakfastMinute = minute(dev.dadyarri.medtracker.reminders.ReminderKind.BREAKFAST),
            lunchMinute = minute(dev.dadyarri.medtracker.reminders.ReminderKind.LUNCH),
            dinnerMinute = minute(dev.dadyarri.medtracker.reminders.ReminderKind.DINNER),
        )
    }
    fun resume(date: LocalDate) = copy(active = true, startDate = date.toString())
    fun configuration(day: Int) = CycleDay.parse(listOf(day1, day2, day3)[day - 1])
    fun withDay(day: Int, value: CycleDay) = when (day) {
        1 -> copy(day1 = value.encode())
        2 -> copy(day2 = value.encode())
        else -> copy(day3 = value.encode())
    }
    fun valid() = runCatching {
        LocalDate.parse(startDate)
        listOf(breakfastMinute, lunchMinute, dinnerMinute).all { it in 0..1439 } &&
            afterMinutes in 1..1440 && (1..3).all { configuration(it) != null }
    }.getOrDefault(false)
}
data class GlucoseTask(val key: String, val at: Long, val food: String, val meal: MealType, val mealId: String?)

object GlucoseCyclePlan {
    fun tasks(cycle: GlucoseCycle, date: LocalDate, meals: List<MealEvent>, zone: ZoneId): List<GlucoseTask> {
        require(cycle.valid())
        val day = cycle.day(date) ?: return emptyList()
        val configuration = cycle.configuration(day)!!
        val type = configuration.meal
        val minute = when (type) {
            MealType.BREAKFAST -> cycle.breakfastMinute
            MealType.LUNCH -> cycle.lunchMinute
            else -> cycle.dinnerMinute
        }
        val first = meals.filter { it.type == type.name &&
            Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == date }
            .minWithOrNull(compareBy<MealEvent> { it.startedAt }.thenBy { it.createdAt }.thenBy { it.id })
        return buildList {
            if (configuration.before != "NONE") add(GlucoseTask("$date-before", date.atStartOfDay().plusMinutes(minute.toLong()).atZone(zone).toInstant().minusSeconds(30 * 60).toEpochMilli(),
                configuration.before, type, null))
            if (configuration.after && first != null) add(GlucoseTask("$date-after",
                Instant.ofEpochMilli(first.startedAt).plusSeconds(cycle.afterMinutes * 60L).toEpochMilli(),
                "AFTER", type, first.id))
        }
    }
    fun elapsedMinutes(measuredAt: Long, meal: MealEvent): Long =
        Duration.between(Instant.ofEpochMilli(meal.startedAt), Instant.ofEpochMilli(measuredAt)).toMinutes()
}
@Dao
interface MealDao {
    @Query("SELECT * FROM meal_event ORDER BY startedAt DESC, createdAt DESC, id")
    fun observe(): Flow<List<MealEvent>>
    @Query("SELECT * FROM meal_event WHERE id = :id") suspend fun get(id: String): MealEvent?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(meal: MealEvent): Long
    @Update suspend fun update(meal: MealEvent): Int
    @Query("UPDATE glucose SET mealEventId = NULL WHERE mealEventId = :id") suspend fun unlink(id: String)
    @Query("DELETE FROM meal_event WHERE id = :id") suspend fun delete(id: String): Int
    @Query("SELECT * FROM glucose_cycle WHERE id = 1") fun cycle(): Flow<GlucoseCycle?>
    @Upsert suspend fun saveCycle(cycle: GlucoseCycle)
}
