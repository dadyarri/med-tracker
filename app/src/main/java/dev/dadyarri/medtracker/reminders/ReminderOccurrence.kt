package dev.dadyarri.medtracker.reminders

import androidx.room.*
import dev.dadyarri.medtracker.alarm.*
import dev.dadyarri.medtracker.meals.*
import kotlinx.coroutines.flow.Flow
import java.time.*

@Entity(tableName = "reminder_occurrence", indices = [Index("nextAt")])
data class ReminderOccurrence(
    @PrimaryKey val key: String,
    val source: String,
    val revision: String,
    val title: String,
    val dueAt: Long,
    val nextAt: Long?,
    val endAt: Long,
    val status: String = "WAITING",
    val token: String = "",
    val snoozes: Int = 0,
    val diagnostic: String? = null,
    @ColumnInfo(defaultValue = "NULL") val food: String? = null,
    @ColumnInfo(defaultValue = "NULL") val meal: String? = null,
    @ColumnInfo(defaultValue = "NULL") val mealEventId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val recordId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val completionSignature: String? = null,
) {
    fun plan() = AlarmPlan(token, nextAt, endAt, true, AlarmStatus.valueOf(status), snoozes)
    fun withPlan(plan: AlarmPlan) = copy(nextAt = plan.nextAt, endAt = plan.endAt,
        status = plan.status.name, token = plan.token, snoozes = plan.snoozes)
    val active get() = status !in listOf("CANCELLED", "EXPIRED", "COMPLETED")
    val canRecord get() = status !in listOf("CANCELLED", "COMPLETED")
}

@Dao
interface ReminderOccurrenceDao {
    @Query("SELECT * FROM reminder_occurrence ORDER BY dueAt, `key`")
    suspend fun all(): List<ReminderOccurrence>
    @Query("SELECT * FROM reminder_occurrence ORDER BY dueAt, `key`")
    fun observe(): Flow<List<ReminderOccurrence>>
    @Upsert suspend fun save(values: List<ReminderOccurrence>)
}

object ReminderSchedule {
    fun desired(settings: List<ReminderSetting>, cycle: GlucoseCycle?, meals: List<MealEvent>,
                now: Instant, zone: ZoneId): List<ReminderOccurrence> = buildList {
        val today = now.atZone(zone).toLocalDate()
        fun addTask(key: String, source: String, revision: String, title: String, at: Long,
                    food: String? = null, meal: String? = null, mealEventId: String? = null) {
            add(ReminderOccurrence(key, source, revision, title, at, at, at + 120 * 60_000L,
                food = food, meal = meal, mealEventId = mealEventId))
        }
        settings.filterNot { it.paused }.forEach { setting ->
            val revision = if (setting.kind == ReminderKind.WEIGHT)
                "${setting.minute}/${setting.weeks}/${setting.anchor}" else setting.minute.toString()
            if (setting.kind == ReminderKind.WEIGHT) {
                // Keep today's occurrence in the desired set after its delivery, too.
                val start = today.minusDays(1).atStartOfDay(zone).toInstant()
                val at = setting.next(start, zone)!!
                addTask("WEIGHT/${at.atZone(zone).toLocalDate()}", "WEIGHT", revision, "Вес", at.toEpochMilli())
                val following = setting.next(at.plusMillis(1), zone)!!
                addTask("WEIGHT/${following.atZone(zone).toLocalDate()}", "WEIGHT", revision, "Вес", following.toEpochMilli())
            } else for (offset in -1L..8L) {
                val date = today.plusDays(offset)
                val at = date.atTime(setting.minute / 60, setting.minute % 60).atZone(zone).toInstant()
                addTask("${setting.kind}/$date", setting.kind.name, revision, setting.kind.label, at.toEpochMilli(),
                    meal = dev.dadyarri.medtracker.measurements.MealType.entries.find { it.name == setting.kind.name }?.name)
            }
        }
        if (cycle?.active == true) {
            val effective = cycle.withMealTimes(settings)
            for (offset in -1L..8L) {
                val date = today.plusDays(offset)
                GlucoseCyclePlan.tasks(effective, date, meals, zone).forEach { task ->
                    val relation = when (task.food) { "FASTING" -> "натощак"; "BEFORE" -> "до еды"; else -> "после еды" }
                    addTask("GLUCOSE/${task.key}", "GLUCOSE",
                        "${effective.startDate}/${task.at}/${task.mealId}/${effective.afterMinutes}/${task.food}/${task.meal}",
                        "Глюкоза · $relation", task.at, task.food, task.meal.name, task.mealId)
                }
            }
        }
    }

    /** New schedules never ring retroactively. Existing overdue rows expire without audio. */
    fun merge(old: List<ReminderOccurrence>, desired: List<ReminderOccurrence>, now: Long,
              recovery: Boolean = false): List<ReminderOccurrence> {
        val wanted = desired.associateBy { it.key }
        val previous = old.associateBy { it.key }
        val result = old.map { row ->
            val candidate = wanted[row.key]
            when {
                row.status in listOf("CANCELLED", "COMPLETED") -> row
                candidate == null || candidate.revision != row.revision ->
                    row.copy(nextAt = null, status = "CANCELLED", token = "", revision = "")
                !row.active -> row
                row.endAt <= now || (row.nextAt?.let { it < now - AlarmPolicy.DELIVERY_GRACE_MS } == true) ||
                    (recovery && row.nextAt?.let { it <= now } == true) ->
                    row.copy(nextAt = null, status = "EXPIRED", token = "")
                recovery && row.status == "RINGING" -> row.copy(status = "MUTED")
                else -> row
            }
        }.associateBy { it.key }.toMutableMap()
        desired.forEach { row ->
            val prior = previous[row.key]
            if (prior?.status != "COMPLETED" && (prior == null || prior.revision != row.revision)) {
                if (row.dueAt >= now) result[row.key] = row
                else if (row.source == "GLUCOSE")
                    result[row.key] = row.copy(status = "EXPIRED", nextAt = null)
            }
        }
        return result.values.map { row ->
            val candidate = wanted[row.key]?.takeIf { it.revision == row.revision }
            if (candidate != null) row.copy(food = candidate.food, meal = candidate.meal, mealEventId = candidate.mealEventId)
            else row
        }
    }
}
