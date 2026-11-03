package dev.dadyarri.medtracker.medications

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

fun medicationId() = UUID.randomUUID().toString()
fun doseValue(text: String): String? = runCatching {
    require(text.matches(Regex("[0-9]+([.,][0-9]{1,6})?")))
    text.replace(',', '.').toBigDecimal().also { require(it.signum() > 0) }.stripTrailingZeros().toPlainString()
}.getOrNull()

enum class PillShape(val label: String) {
    NONE("Без изображения"), ROUND("Круг"), OVAL("Овал"), OBLONG("Продолговатая"), DIAMOND("Ромб"), CAPSULE("Капсула"),
    SOFTGEL("Мягкая капсула"), TRIANGLE("Треугольник"), HEXAGON("Шестиугольник"),
    BOTTLE("Флакон"), DROPPER("Пипетка"), TUBE("Тюбик"), SYRINGE("Шприц"),
    INHALER("Ингалятор"), SACHET("Пакетик"), AMPOULE("Ампула");

    val supportsScore get() = this in listOf(ROUND, OVAL, OBLONG, DIAMOND, TRIANGLE, HEXAGON)
    val isContainer get() = this in listOf(BOTTLE, DROPPER, TUBE, SYRINGE, INHALER, SACHET, AMPOULE)
}
enum class RegimenKind(val label: String) { CONTINUOUS("Постоянно"), COURSE("Курс"), ONCE("Разово"), PRN("По необходимости") }
enum class ScheduleMode(val label: String) {
    DAILY("Каждый день"), WEEKDAYS("Дни недели"), DAYS("Каждые N дней"), HOURS("Каждые N часов"), ONCE("Однократно"), PRN("По необходимости")
}
enum class MedicationFood(val label: String) { NONE("Без привязки"), BEFORE("До еды"), DURING("Во время еды"), AFTER("После еды") }

@Entity(tableName = "medication")
data class Medication(
    @PrimaryKey val id: String = medicationId(), val name: String = "",
    val strength: String = "", val unit: String = "табл.",
    val usualDose: String = "1", val instruction: String = "", val note: String = "",
    val shape: String = PillShape.NONE.name, val score: Int = 0,
    val color: Long = 0xFFFFFFFF, val secondColor: Long = 0xFF6750A4,
    val archived: Boolean = false, val createdAt: Long = 0, val updatedAt: Long = 0,
)

@Entity(tableName = "medication_regimen", indices = [Index("medicationId")],
    foreignKeys = [ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medicationId"], onDelete = ForeignKey.RESTRICT)])
data class MedicationRegimen(
    @PrimaryKey val id: String = medicationId(), val medicationId: String,
    val kind: String, val startsAt: Long, val endsAt: Long? = null,
    val completedAt: Long? = null, val createdAt: Long,
) {
    fun activeAt(at: Long) = startsAt <= at && (endsAt == null || at < endsAt) &&
        (completedAt == null || at < completedAt)
}

@Entity(tableName = "regimen_version", indices = [Index(value = ["regimenId", "effectiveAt"], unique = true)],
    foreignKeys = [ForeignKey(entity = MedicationRegimen::class, parentColumns = ["id"], childColumns = ["regimenId"], onDelete = ForeignKey.RESTRICT)])
data class RegimenVersion(
    @PrimaryKey val id: String = medicationId(), val regimenId: String, val effectiveAt: Long,
    val dose: String, val unit: String, val mode: String,
    val minutes: String = "480", val weekdays: String = "1,2,3,4,5,6,7",
    val everyDays: Int = 1, val everyHours: Int = 8, val anchorDate: String,
    val onceAt: Long? = null, val food: String = "NONE", val foodOffset: Boolean = false,
    val meal: String = "BREAKFAST", val offsetMinutes: Int = 30,
) {
    fun times() = minutes.split(',').mapNotNull(String::toIntOrNull).distinct().sorted()
    fun days() = weekdays.split(',').mapNotNull(String::toIntOrNull).toSet()
}

@Entity(tableName = "medication_intake",
    indices = [Index("medicationId"), Index("regimenId"), Index("versionId"), Index("takenAt")],
    foreignKeys = [
        ForeignKey(entity = Medication::class, parentColumns = ["id"], childColumns = ["medicationId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = MedicationRegimen::class, parentColumns = ["id"], childColumns = ["regimenId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = RegimenVersion::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.RESTRICT),
    ])
data class MedicationIntake(
    @PrimaryKey val id: String = medicationId(), val medicationId: String,
    val regimenId: String? = null, val versionId: String? = null,
    val dose: String, val unit: String, val takenAt: Long,
    val createdAt: Long, val updatedAt: Long, val comment: String = "",
)

data class MedicationData(
    val medications: List<Medication> = emptyList(), val regimens: List<MedicationRegimen> = emptyList(),
    val versions: List<RegimenVersion> = emptyList(), val intakes: List<MedicationIntake> = emptyList(),
)
fun List<RegimenVersion>.at(regimenId: String, at: Long) =
    filter { it.regimenId == regimenId && it.effectiveAt <= at }.maxByOrNull { it.effectiveAt }

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medication ORDER BY name COLLATE NOCASE, id") fun medications(): Flow<List<Medication>>
    @Query("SELECT * FROM medication_regimen ORDER BY startsAt, id") fun regimens(): Flow<List<MedicationRegimen>>
    @Query("SELECT * FROM regimen_version ORDER BY effectiveAt, id") fun versions(): Flow<List<RegimenVersion>>
    @Query("SELECT * FROM medication_intake ORDER BY takenAt DESC, createdAt DESC, id") fun intakes(): Flow<List<MedicationIntake>>
    @Query("SELECT * FROM medication WHERE id = :id") suspend fun medication(id: String): Medication?
    @Query("SELECT * FROM medication_regimen WHERE id = :id") suspend fun regimen(id: String): MedicationRegimen?
    @Query("SELECT * FROM medication_intake WHERE id = :id") suspend fun intake(id: String): MedicationIntake?
    @Insert suspend fun insert(value: Medication)
    @Update suspend fun update(value: Medication): Int
    @Insert suspend fun insert(value: MedicationRegimen)
    @Update suspend fun update(value: MedicationRegimen): Int
    @Insert suspend fun insert(value: RegimenVersion)
    @Insert suspend fun insert(value: MedicationIntake)
    @Update suspend fun update(value: MedicationIntake): Int
    @Query("DELETE FROM medication_intake WHERE id = :id") suspend fun deleteIntake(id: String): Int
}
