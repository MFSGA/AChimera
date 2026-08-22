package rs.chimera.android.backend

import rs.chimera.android.backend.model.ServiceState

internal class ProfileRuntimeApplyPendingTracker {
    private var startingPendingApply: Pair<String, Long>? = null

    fun onServiceState(
        state: ServiceState,
        pendingApply: Pair<String, Long>? = null,
    ): Pair<String, Long>? =
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
