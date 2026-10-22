package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class NetworkSettingsAvailabilityPolicyTest {
    @Test
    fun vpnDependentSettingsAreAvailableOnlyWhenVpnIsStoppedOrFailed() {
        assertTrue(NetworkSettingsAvailabilityPolicy.canChangeSettings(ServiceState.STOPPED))
        assertTrue(NetworkSettingsAvailabilityPolicy.canChangeSettings(ServiceState.ERROR))
        assertFalse(NetworkSettingsAvailabilityPolicy.canChangeSettings(ServiceState.STARTING))
        assertFalse(NetworkSettingsAvailabilityPolicy.canChangeSettings(ServiceState.RUNNING))
        assertFalse(NetworkSettingsAvailabilityPolicy.canChangeSettings(ServiceState.STOPPING))
    }
}
