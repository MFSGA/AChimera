package rs.chimera.android.service

import rs.chimera.android.backend.BackendRuntimeState
import rs.chimera.android.backend.model.ServiceState

internal data class VpnRuntimeCleanupOutcome(
    val performed: Boolean,
    val releaseFailure: Throwable? = null,
)

internal class VpnRuntimeCleanupCoordinator(
    private val lifecycleGate: VpnLifecycleGate,
    private val runtimeControl: VpnRuntimeControl,
    private val releaseRuntime: (stopCore: Boolean, removeForeground: Boolean) -> Throwable?,
    private val publishVpnInactive: () -> Unit,
    private val formatReleaseFailure: (Throwable) -> String,
    private val logCleanupComplete: () -> Unit,
) {
    fun cleanup(
        finalState: ServiceState = ServiceState.STOPPED,
        stopCore: Boolean = true,
        errorMessage: String? = null,
    ): VpnRuntimeCleanupOutcome {
        if (!lifecycleGate.beginCleanup()) {
            return VpnRuntimeCleanupOutcome(performed = false)
        }

        val releaseFailure = releaseRuntime(stopCore, true)
        val decision = VpnRuntimeCleanupPolicy.resolve(
            requestedState = finalState,
            existingErrorMessage = errorMessage,
            releaseFailure = releaseFailure,
            formatReleaseFailure = formatReleaseFailure,
        )
        decision.errorMessage?.let(BackendRuntimeState::updateServiceError)
        publishVpnInactive()
        VpnRuntimeRegistry.unregister(runtimeControl)
        BackendRuntimeState.updateServiceState(decision.serviceState)
        logCleanupComplete()
        return VpnRuntimeCleanupOutcome(performed = true, releaseFailure = releaseFailure)
    }
}
