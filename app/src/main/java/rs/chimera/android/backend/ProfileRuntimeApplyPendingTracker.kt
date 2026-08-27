package rs.chimera.android.backend

import rs.chimera.android.backend.model.ServiceState

internal class ProfileRuntimeApplyPendingTracker {
    private var startingPendingApply: ProfileRuntimeApplyPendingToken? = null

    fun onServiceState(
        state: ServiceState,
        pendingApply: ProfileRuntimeApplyPendingToken? = null,
    ): ProfileRuntimeApplyPendingToken? =
        when (state) {
            ServiceState.STARTING -> {
                startingPendingApply = pendingApply
                null
            }

            ServiceState.RUNNING -> {
                startingPendingApply.also { startingPendingApply = null }
            }

            ServiceState.STOPPING,
            ServiceState.STOPPED,
            ServiceState.ERROR,
            -> {
                startingPendingApply = null
                null
            }
        }
}
