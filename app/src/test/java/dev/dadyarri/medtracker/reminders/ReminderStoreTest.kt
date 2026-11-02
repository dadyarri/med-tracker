package dev.dadyarri.medtracker.reminders

import android.app.Application
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ReminderStoreTest {
    @Test fun settingsRoundTripPreservesPausedScheduleAndDoesNotChangeOtherKinds() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val store = ReminderSettingsStore(context)
        val original = store.settings.first()
        val setting = ReminderSetting(ReminderKind.WEIGHT, false, 570, 3, LocalDate.of(2026, 11, 3))
        store.save(setting)
        assertEquals(setting, ReminderSettingsStore(context).settings.first().single { it.kind == ReminderKind.WEIGHT })
        store.save(setting.copy(paused = true))
        val values = store.settings.first()
        assertEquals(setting.copy(paused = true), values.single { it.kind == ReminderKind.WEIGHT })
        assertEquals(original.filter { it.kind != ReminderKind.WEIGHT }, values.filter { it.kind != ReminderKind.WEIGHT })
        store.save(original.single { it.kind == ReminderKind.WEIGHT })
    }
}
