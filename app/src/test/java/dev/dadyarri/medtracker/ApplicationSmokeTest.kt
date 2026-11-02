package dev.dadyarri.medtracker

import android.os.Looper
import dev.dadyarri.medtracker.alarm.AlarmController
import dev.dadyarri.medtracker.alarm.AlarmDatabase
import kotlinx.coroutines.cancel
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** JVM lifecycle smoke test; not a claim about physical-device rendering or audio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = MedTrackerApplication::class)
class ApplicationSmokeTest {
    @Test fun launcherActivityAndDependencyGraphStartWithoutPermissions() {
        try {
            val activity = Robolectric.buildActivity(MainActivity::class.java).setup()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(activity.get().isFinishing)
            activity.pause().stop().destroy()
        } finally {
            GlobalContext.get().get<AlarmController>().scope.cancel()
            GlobalContext.get().get<AlarmDatabase>().close()
            stopKoin()
        }
    }
}
