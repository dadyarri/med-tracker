package dev.dadyarri.medtracker.alarm

import androidx.room.*
import dev.dadyarri.medtracker.pressure.PressureRecord
import dev.dadyarri.medtracker.pressure.PressureDao
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "test_alarm")
data class AlarmRecord(
    @PrimaryKey val id: Int = 1,
    val token: String,
    val nextAt: Long?,
    val endAt: Long,
    val sound: Boolean,
    val status: String,
    val snoozes: Int,
    val repeatMinutes: Int,
    val snoozeMinutes: Int,
    val diagnostic: String? = null,
) {
    fun plan() = AlarmPlan(token, nextAt, endAt, sound, AlarmStatus.valueOf(status), snoozes, repeatMinutes, snoozeMinutes)
}

fun AlarmPlan.record(diagnostic: String? = null) = AlarmRecord(
    token = token, nextAt = nextAt, endAt = endAt, sound = sound, status = status.name,
    snoozes = snoozes, repeatMinutes = repeatMinutes, snoozeMinutes = snoozeMinutes, diagnostic = diagnostic,
)

@Entity(tableName = "alarm_event")
data class AlarmEvent(@PrimaryKey(autoGenerate = true) val id: Long = 0, val at: Long, val description: String)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM test_alarm WHERE id = 1") suspend fun current(): AlarmRecord?
    @Query("SELECT * FROM test_alarm WHERE id = 1") fun observe(): Flow<AlarmRecord?>
    @Query("SELECT * FROM alarm_event ORDER BY id DESC LIMIT 40") fun events(): Flow<List<AlarmEvent>>
    @Upsert suspend fun save(record: AlarmRecord)
    @Insert suspend fun event(event: AlarmEvent)
}

@Database(entities = [AlarmRecord::class, AlarmEvent::class, PressureRecord::class], version = 2,
    exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2)])
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun pressure(): PressureDao
}
