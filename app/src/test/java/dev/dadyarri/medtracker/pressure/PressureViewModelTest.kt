package dev.dadyarri.medtracker.pressure

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class PressureViewModelTest {
    @Test fun doubleSaveDoesNotDuplicateAndEditPreservesOriginalTimestamps() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AlarmDatabase::class.java).build()
        val model = PressureViewModel(db.pressure())
        try {
            val draft = PressureDraft("120", "80", time = "03.11.2026 08:45")
            var completed = 0
            model.save("stable-id", false, draft) { completed++ }
            model.save("stable-id", false, draft) { completed++ }
            await { completed > 0 && !model.busy.value }
            val original = db.pressure().get("stable-id")!!
            // Include seconds: editing another field must not truncate measuredAt.
            val precise = original.copy(measuredAt = original.measuredAt + 12_345)
            db.pressure().update(precise)
            model.save("stable-id", true, PressureDraft.from(precise).copy(comment = "Изменение")) { completed++ }
            await { !model.busy.value }
            val edited = db.pressure().get("stable-id")!!
            assertEquals(precise.measuredAt, edited.measuredAt)
            assertEquals(precise.createdAt, edited.createdAt)
            assertEquals("Изменение", edited.comment)
        } finally { model.viewModelScope.cancel(); db.close() }
    }

    private fun await(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("Expected database operation to finish", condition())
    }
}
