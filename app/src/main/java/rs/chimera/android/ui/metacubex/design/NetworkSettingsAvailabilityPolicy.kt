package rs.chimera.android.ui.metacubex.design

import rs.chimera.android.backend.model.ServiceState

internal object NetworkSettingsAvailabilityPolicy {
    fun canChangeSettings(serviceState: ServiceState): Boolean =
        serviceState == ServiceState.STOPPED || serviceState == ServiceState.ERROR
}
