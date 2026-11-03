package dev.dadyarri.medtracker.medications

import android.app.Application
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MedicationServiceTest {
    private lateinit var db: AlarmDatabase
    private lateinit var service: MedicationService
    private val medication = Medication("m", "Препарат", unit = "табл.", usualDose = "0,5", shape = "CAPSULE",
        color = 0xFFB8DFFF, secondColor = 0xFFFFDAD6)
    private val regimen = MedicationRegimen("r", "m", "CONTINUOUS", 1000, createdAt = 1)
    private val version = RegimenVersion("v1", "r", 1000, "0.5", "табл.", "HOURS", everyHours = 8, anchorDate = "2026-11-03")
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlarmDatabase::class.java).allowMainThreadQueries().build()
        service = MedicationService(db)
        service.save(medication)
        service.prescribe(regimen, version)
    }
    @After fun cleanup() { db.close() }
    private fun fact(id: String = "i", takenAt: Long = 2000) =
        MedicationIntake(id, "m", "r", null, "0,5", "табл.", takenAt, 0, 0)
    private suspend fun rejected(operation: suspend () -> Unit) {
        try { operation(); fail("Expected invalid operation") } catch (_: IllegalArgumentException) { }
    }
    @Test fun fractionalFactsAndVisualParametersRoundTripWithoutUnitConversion() = runBlocking {
        service.saveIntake(fact(), false)
        assertEquals("0.5", db.medications().intake("i")!!.dose)
        assertEquals("CAPSULE", db.medications().medication("m")!!.shape)
        assertEquals(medication.secondColor, db.medications().medication("m")!!.secondColor)
        rejected { service.save(medication.copy(unit = "мг")) }
        assertEquals("табл.", db.medications().medication("m")!!.unit)
        assertNull(doseValue("0")); assertNull(doseValue("-1")); assertNull(doseValue("1e3"))
        assertEquals("2.5", doseValue("2,500"))
    }
    @Test fun allAppearanceKindsRoundTripAndOnlyTabletsHaveScores() = runBlocking {
        for (shape in PillShape.entries) {
            val value = medication.copy(id = "shape_${shape.name}", shape = shape.name,
                score = if (shape.supportsScore) 2 else 0)
            service.save(value)
            assertEquals(shape.name, db.medications().medication(value.id)!!.shape)
            if (!shape.supportsScore) rejected { service.save(value.copy(score = 1)) }
        }
    }
    @Test fun revisionsKeepOldIntakeSnapshotsAndEditingPreservesCreationTime() = runBlocking {
        service.saveIntake(fact(), false)
        val original = db.medications().intake("i")!!
        service.revise(version.copy(id = "v2", effectiveAt = 3000, dose = "1", everyHours = 12))
        service.saveIntake(fact("new", 4000), false)
        assertEquals("v1", db.medications().intake("i")!!.versionId)
        assertEquals("v2", db.medications().intake("new")!!.versionId)
        service.saveIntake(original.copy(dose = "0.25", takenAt = 2500), true)
        assertEquals(original.createdAt, db.medications().intake("i")!!.createdAt)
        assertEquals("0.25", db.medications().intake("i")!!.dose)
        assertEquals("0.5", db.medications().versions().first().first().dose)
        service.deleteIntake("new")
        assertEquals(listOf("i"), db.medications().intakes().first().map { it.id })
    }
    @Test fun duplicateEffectiveDatesAndCrossDrugAssignmentsAreRejected() = runBlocking {
        service.revise(version.copy(id = "v2", effectiveAt = 3000))
        rejected { service.revise(version.copy(id = "duplicate", effectiveAt = 3000)) }
        service.save(Medication("other", "Другой препарат"))
        rejected { service.saveIntake(fact().copy(medicationId = "other"), false) }
        assertEquals(2, db.medications().versions().first().size)
        assertTrue(db.medications().intakes().first().isEmpty())
    }
    @Test fun prnDoesNotImposeIntervalsAndFinishedCourseCanReceiveHistoricalFacts() = runBlocking {
        val prn = regimen.copy(id = "prn", kind = "PRN")
        service.prescribe(prn, version.copy(id = "p", regimenId = "prn", mode = "PRN"))
        service.saveIntake(fact("one").copy(regimenId = "prn"), false)
        service.saveIntake(fact("two", 2001).copy(regimenId = "prn"), false)
        service.finish("r", 2500)
        service.saveIntake(fact("historical", 2000), false)
        assertEquals(3, db.medications().intakes().first().size)
        assertEquals(2500L, db.medications().regimen("r")!!.completedAt)
    }
    @Test fun repeatingCourseUsesNewIdentityAndArchiveDoesNotEraseHistory() = runBlocking {
        val old = regimen.copy(id = "course", kind = "COURSE", endsAt = 5000)
        service.prescribe(old, version.copy(id = "course-v1", regimenId = "course", mode = "DAILY"))
        service.finish(old.id, 4000)
        val repeated = old.copy(id = "repeated", startsAt = 6000, endsAt = 10000, completedAt = null)
        service.prescribe(repeated, version.copy(id = "repeat-v1", regimenId = repeated.id, effectiveAt = 6000, mode = "DAILY"))
        service.saveIntake(fact(), false)
        service.archive("m", true)
        assertEquals(4000L, db.medications().regimen("course")!!.completedAt)
        assertEquals(1, db.medications().intakes().first().size)
        service.archive("m", false)
        assertFalse(db.medications().medication("m")!!.archived)
        assertNotNull(db.medications().regimen("r")!!.completedAt)
    }
    @Test fun invalidPrescriptionDoesNotLeavePartialRegimen() = runBlocking {
        rejected { service.prescribe(regimen.copy(id = "bad"), version.copy(id = "bad-v", regimenId = "bad", dose = "0")) }
        assertNull(db.medications().regimen("bad"))
        assertEquals(1, db.medications().versions().first().size)
    }
}
