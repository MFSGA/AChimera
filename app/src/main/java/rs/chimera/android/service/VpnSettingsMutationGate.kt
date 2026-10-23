package rs.chimera.android.service

import rs.chimera.android.backend.VpnSettingsAvailabilityPolicy
import rs.chimera.android.backend.model.ServiceState

/** Serializes settings writes with VPN start requests and rejects writes while runtime changes are unsafe. */
internal class VpnSettingsMutationGate(
    private val lock: Any,
    private val hasRunRequest: () -> Boolean,
) {
    fun <T> withSettingsMutation(
        serviceState: ServiceState,
        unavailableMessage: () -> String,
        action: () -> T,
    ): T = synchronized(lock) {
        check(VpnSettingsAvailabilityPolicy.canChangeSettings(serviceState, hasRunRequest())) {
            unavailableMessage()
        }
        action()
    }
}
