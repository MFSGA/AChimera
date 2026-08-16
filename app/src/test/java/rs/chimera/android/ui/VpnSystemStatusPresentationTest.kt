package rs.chimera.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.VpnSystemStatus

class VpnSystemStatusPresentationTest {
    @Test
    fun unobservedStatusHasNoRestriction() {
        val presentation = VpnSystemStatus().resolvePresentation()

        assertEquals(VpnSystemStatusDisplayMode.UNOBSERVED, presentation.mode)
        assertFalse(presentation.restricted)
    }

    @Test
    fun activeObservationIsCurrent() {
        val presentation = VpnSystemStatus(
            observed = true,
            serviceActive = true,
            alwaysOn = true,
        ).resolvePresentation()

        assertEquals(VpnSystemStatusDisplayMode.CURRENT, presentation.mode)
        assertTrue(presentation.alwaysOn)
    }

    @Test
    fun inactiveLockdownObservationRemainsRestrictedAndHistorical() {
        val presentation = VpnSystemStatus(
            observed = true,
            serviceActive = false,
            lockdown = true,
        ).resolvePresentation()

        assertEquals(VpnSystemStatusDisplayMode.LAST_OBSERVED, presentation.mode)
        assertTrue(presentation.restricted)
    }
}
