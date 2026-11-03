package dev.dadyarri.medtracker.alarm

import androidx.room.*
import dev.dadyarri.medtracker.meals.*
import dev.dadyarri.medtracker.measurements.WeightRecord
import dev.dadyarri.medtracker.measurements.GlucoseRecord
import dev.dadyarri.medtracker.measurements.MeasurementDao
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

class PreserveGlucoseCompletions : androidx.room.migration.AutoMigrationSpec {
    override fun onPostMigrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""UPDATE reminder_occurrence SET completionSignature =
            (SELECT CAST(measuredAt AS TEXT) || '/' || foodContext || '/' || COALESCE(meal, '') || '/' || COALESCE(mealEventId, '')
             FROM glucose WHERE glucose.id = reminder_occurrence.recordId)
            WHERE source = 'GLUCOSE' AND status = 'COMPLETED'""")
    }
}

@DeleteColumn(tableName = "medication", columnName = "form")
class RemoveMedicationForm : androidx.room.migration.AutoMigrationSpec

@Database(entities = [AlarmRecord::class, AlarmEvent::class, PressureRecord::class, WeightRecord::class, GlucoseRecord::class, MealEvent::class, GlucoseCycle::class, dev.dadyarri.medtracker.reminders.ReminderOccurrence::class,
    dev.dadyarri.medtracker.medications.Medication::class, dev.dadyarri.medtracker.medications.MedicationRegimen::class,
    dev.dadyarri.medtracker.medications.RegimenVersion::class, dev.dadyarri.medtracker.medications.MedicationIntake::class], version = 10,
    exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6), AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8, spec = PreserveGlucoseCompletions::class), AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10, spec = RemoveMedicationForm::class)])
abstract class AlarmDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun pressure(): PressureDao
    abstract fun measurements(): MeasurementDao
    abstract fun medications(): dev.dadyarri.medtracker.medications.MedicationDao
    abstract fun meals(): MealDao
    abstract fun reminders(): dev.dadyarri.medtracker.reminders.ReminderOccurrenceDao
}
