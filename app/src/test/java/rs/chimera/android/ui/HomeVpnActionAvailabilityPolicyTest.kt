package rs.chimera.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeVpnActionAvailabilityPolicyTest {
    @Test
    fun actionsRemainAvailableWhenVpnIsStable() {
        assertTrue(
            HomeVpnActionAvailabilityPolicy.isEnabled(
                isVpnTransitioning = false,
                isVpnActionInProgress = false,
            ),
        )
    }

    @Test
    fun actionsAreDisabledDuringBackendTransition() {
        assertFalse(
            HomeVpnActionAvailabilityPolicy.isEnabled(
                isVpnTransitioning = true,
                isVpnActionInProgress = false,
            ),
        )
    }

    @Test
    fun actionsAreDisabledBeforeBackendTransitionBegins() {
        assertFalse(
            HomeVpnActionAvailabilityPolicy.isEnabled(
                isVpnTransitioning = false,
                isVpnActionInProgress = true,
            ),
        )
    }

    @Test
    fun actionsAreDisabledWhenUiSwitchGuardRejectsNavigation() {
        assertFalse(
            HomeVpnActionAvailabilityPolicy.isEnabled(
                isVpnTransitioning = false,
                isVpnActionInProgress = false,
                isUiSwitchAllowed = false,
            ),
        )
    }
}
