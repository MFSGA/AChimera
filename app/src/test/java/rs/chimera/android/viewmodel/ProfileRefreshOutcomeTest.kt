package rs.chimera.android.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRefreshOutcomeTest {
    @Test
    fun onlyAppliedRefreshPublishesOperationStatus() {
        assertTrue(ProfileRefreshOutcome.APPLIED.shouldPublishOperationStatus)
        assertFalse(ProfileRefreshOutcome.STALE.shouldPublishOperationStatus)
        assertFalse(ProfileRefreshOutcome.FAILED.shouldPublishOperationStatus)
    }
}
