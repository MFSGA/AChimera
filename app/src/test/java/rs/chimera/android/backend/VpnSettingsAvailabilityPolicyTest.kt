package rs.chimera.android.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class VpnSettingsAvailabilityPolicyTest {
    @Test
    fun vpnDependentSettingsAreAvailableOnlyWhenVpnIsStoppedOrFailed() {
        assertTrue(VpnSettingsAvailabilityPolicy.canChangeSettings(ServiceState.STOPPED))
        assertTrue(VpnSettingsAvailabilityPolicy.canChangeSettings(ServiceState.ERROR))
        assertFalse(VpnSettingsAvailabilityPolicy.canChangeSettings(ServiceState.STOPPED, hasRunRequest = true))
        assertFalse(VpnSettingsAvailabilityPolicy.canChangeSettings(ServiceState.STARTING))
        assertFalse(VpnSettingsAvailabilityPolicy.canChangeSettings(ServiceState.RUNNING))
        assertFalse(VpnSettingsAvailabilityPolicy.canChangeSettings(ServiceState.STOPPING))
    }
}
