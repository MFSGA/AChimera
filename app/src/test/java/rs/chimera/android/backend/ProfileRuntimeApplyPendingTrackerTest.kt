package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class ProfileRuntimeApplyPendingTrackerTest {
    @Test
    fun initialStartingStateIsAppliedWhenServiceBecomesRunning() {
        val tracker = ProfileRuntimeApplyPendingTracker()
        val pending = token()

        assertNull(tracker.onServiceState(ServiceState.STARTING, pending))
        assertEquals(pending, tracker.onServiceState(ServiceState.RUNNING))
        assertNull(tracker.onServiceState(ServiceState.RUNNING))
    }

    @Test
    fun stoppedServiceDropsCapturedPendingApply() {
        val tracker = ProfileRuntimeApplyPendingTracker()

        tracker.onServiceState(ServiceState.STARTING, token())
        tracker.onServiceState(ServiceState.STOPPED)

        assertNull(tracker.onServiceState(ServiceState.RUNNING))
    }

    private fun token() = ProfileRuntimeApplyPendingToken(
        profileId = "active",
        profileRevision = 42L,
        sourceFingerprint = "source",
    )
}
