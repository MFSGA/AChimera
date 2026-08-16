package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.VpnSystemStatus

class VpnSystemStatusPolicyTest {
    @Test
    fun unsupportedApiLevelsDoNotReportUnavailableSystemModes() {
        val status = VpnSystemStatusPolicy.observed(
            apiLevel = 28,
            serviceActive = true,
            alwaysOn = true,
            lockdown = true,
            observedAt = 1_000,
        )

        assertTrue(status.observed)
        assertTrue(status.serviceActive)
        assertFalse(status.alwaysOn)
        assertFalse(status.lockdown)
    }

    @Test
    fun supportedApiLevelsPreserveObservedModes() {
        val status = VpnSystemStatusPolicy.observed(
            apiLevel = 29,
            serviceActive = true,
            alwaysOn = true,
            lockdown = true,
            observedAt = 2_000,
        )

        assertTrue(status.alwaysOn)
        assertTrue(status.lockdown)
        assertEquals(2_000, status.observedAt)
    }

    @Test
    fun inactiveStateKeepsLastObservationWithoutClaimingLiveService() {
        val previous = VpnSystemStatus(
            observed = true,
            serviceActive = true,
            alwaysOn = true,
            lockdown = false,
            observedAt = 3_000,
        )

        val inactive = VpnSystemStatusPolicy.inactive(previous)

        assertTrue(inactive.observed)
        assertFalse(inactive.serviceActive)
        assertTrue(inactive.alwaysOn)
        assertEquals(3_000, inactive.observedAt)
    }
}
