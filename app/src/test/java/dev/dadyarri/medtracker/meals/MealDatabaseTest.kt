package dev.dadyarri.medtracker.meals

import android.app.Application
import androidx.room.Room
import androidx.room.withTransaction
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import dev.dadyarri.medtracker.measurements.GlucoseRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class MealDatabaseTest {
    @Test fun editingMealRecalculatesIntervalAndDeletionOnlyRemovesLink() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlarmDatabase::class.java).build()
        try {
            val meal = MealEvent("m", "BREAKFAST", 1_000_000, 2, 2)
            db.meals().insert(meal)
            assertEquals(-1L, db.meals().insert(meal))
            val glucose = GlucoseRecord("g", 550, "MMOL", "AFTER", null, "", meal.startedAt + 175 * 60_000, 3, 3,
                "BREAKFAST", "m")
            db.measurements().insert(glucose)
            assertEquals(175L, GlucoseCyclePlan.elapsedMinutes(glucose.measuredAt, db.meals().get("m")!!))
            db.meals().update(meal.copy(startedAt = meal.startedAt + 10 * 60_000))
            assertEquals(165L, GlucoseCyclePlan.elapsedMinutes(glucose.measuredAt, db.meals().get("m")!!))
            val cycle = GlucoseCycle(active = true, startDate = "2026-11-03")
            db.meals().saveCycle(cycle)
            assertEquals(cycle, db.meals().cycle().first())
            db.withTransaction { db.meals().unlink("m"); db.meals().delete("m") }
            assertNull(db.meals().get("m"))
            assertEquals(glucose.copy(mealEventId = null), db.measurements().glucose("g"))
        } finally { db.close() }
    }
}
