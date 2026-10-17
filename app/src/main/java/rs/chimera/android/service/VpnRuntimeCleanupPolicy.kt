package rs.chimera.android.service

import rs.chimera.android.backend.model.ServiceState

internal data class VpnRuntimeCleanupDecision(
    val serviceState: ServiceState,
    val errorMessage: String?,
)

internal object VpnRuntimeCleanupPolicy {
    fun resolve(
        requestedState: ServiceState,
        existingErrorMessage: String?,
        releaseFailure: Throwable?,
        formatReleaseFailure: (Throwable) -> String,
    ): VpnRuntimeCleanupDecision {
        val errorMessage = existingErrorMessage ?: releaseFailure?.let(formatReleaseFailure)
        val serviceState = if (errorMessage != null || releaseFailure != null) {
            ServiceState.ERROR
        } else {
            requestedState
        }
        return VpnRuntimeCleanupDecision(
            serviceState = serviceState,
            errorMessage = errorMessage,
        )
    }
}
