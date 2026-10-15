package rs.chimera.android.backend

import rs.chimera.android.backend.model.ServiceState

internal object ProfileDeletionRuntimePolicy {
    enum class BlockReason {
        VPN_TRANSITION,
        ACTIVE_PROFILE_IN_USE,
    }

    fun blockReason(
        targetIsActive: Boolean,
        serviceState: ServiceState,
        hasRunRequest: Boolean,
    ): BlockReason? = when {
        serviceState == ServiceState.STARTING || serviceState == ServiceState.STOPPING ->
            BlockReason.VPN_TRANSITION
        !targetIsActive -> null
        serviceState == ServiceState.STOPPED && !hasRunRequest -> null
        else -> BlockReason.ACTIVE_PROFILE_IN_USE
    }
}
