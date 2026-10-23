package rs.chimera.android.backend

import rs.chimera.android.backend.model.ServiceState

internal object VpnSettingsAvailabilityPolicy {
    fun canChangeSettings(serviceState: ServiceState, hasRunRequest: Boolean = false): Boolean =
        !hasRunRequest && (serviceState == ServiceState.STOPPED || serviceState == ServiceState.ERROR)
}
