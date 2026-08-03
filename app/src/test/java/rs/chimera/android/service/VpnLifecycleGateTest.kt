package rs.chimera.android.service

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnLifecycleGateTest {
    @Test
    fun stopDuringStartingCancelsRegisteredStartup() {
        val gate = VpnLifecycleGate()
        val startup = Job()

        assertTrue(gate.requestStart())
        assertTrue(gate.registerStartup(startup))

        val stop = gate.requestStop()

        assertTrue(stop.accepted)
        assertSame(startup, stop.startupJob)
        assertTrue(startup.isCancelled)
        assertFalse(gate.canHandleUnexpectedCoreStop())
    }

    @Test
    fun stopBeforeStartupRegistrationCancelsLateJob() {
        val gate = VpnLifecycleGate()
        val lateStartup = Job()

        assertTrue(gate.requestStart())
        val stop = gate.requestStop()

        assertTrue(stop.accepted)
        assertNull(stop.startupJob)
        assertFalse(gate.registerStartup(lateStartup))
        assertTrue(lateStartup.isCancelled)
    }

    @Test
    fun rapidRepeatedStartAndStopRequestsAreIdempotent() {
        val gate = VpnLifecycleGate()
        val startup = Job()

        assertTrue(gate.requestStart())
        assertFalse(gate.requestStart())
        assertTrue(gate.registerStartup(startup))

        assertTrue(gate.requestStop().accepted)
        assertFalse(gate.requestStop().accepted)
        assertFalse(gate.requestStart())
        assertFalse(gate.canHandleUnexpectedCoreStop())
    }

    @Test
    fun cleanupCanOnlyBeClaimedOnce() {
        val gate = VpnLifecycleGate()

        assertTrue(gate.beginCleanup())
        assertFalse(gate.beginCleanup())
        assertFalse(gate.requestStart())
        assertFalse(gate.requestStop().accepted)
    }

    @Test
    fun clearedStartupIsNotReturnedByStopRequest() {
        val gate = VpnLifecycleGate()
        val startup = Job()

        assertTrue(gate.requestStart())
        assertTrue(gate.registerStartup(startup))
        gate.clearStartup(startup)

        val stop = gate.requestStop()

        assertTrue(stop.accepted)
        assertNull(stop.startupJob)
        assertFalse(startup.isCancelled)
    }
}
