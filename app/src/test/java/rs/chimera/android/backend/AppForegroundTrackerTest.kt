package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class AppForegroundTrackerTest {
    @Test
    fun activityCounterTracksMultipleStartedActivities() {
        val counter = ForegroundActivityCounter()

        assertEquals(false, counter.isForeground.value)

        counter.onActivityStarted()
        counter.onActivityStarted()
        assertEquals(true, counter.isForeground.value)

        counter.onActivityStopped()
        assertEquals(true, counter.isForeground.value)

        counter.onActivityStopped()
        assertEquals(false, counter.isForeground.value)
    }

    @Test
    fun extraStopDoesNotUnderflowForegroundCounter() {
        val counter = ForegroundActivityCounter()

        counter.onActivityStopped()
        counter.onActivityStarted()

        assertEquals(true, counter.isForeground.value)
    }

    @Test
    fun telemetryGatePollsOnlyWhileServiceRunsInForegroundApp() {
        assertTrue(shouldPollRuntimeTelemetry(ServiceState.RUNNING, true))
        assertFalse(shouldPollRuntimeTelemetry(ServiceState.RUNNING, false))
        assertFalse(shouldPollRuntimeTelemetry(ServiceState.STOPPED, true))
        assertFalse(shouldPollRuntimeTelemetry(ServiceState.ERROR, false))
    }
}
