package rs.chimera.android.backend

import rs.chimera.android.backend.model.ServiceState

internal object RuntimeOperationPolicy {
    fun requireRunning(
        serviceState: ServiceState,
        notRunningMessage: () -> String,
    ) {
        check(serviceState == ServiceState.RUNNING, notRunningMessage)
    }
}
