package rs.chimera.android.service

import rs.chimera.android.backend.model.VpnSystemStatus

internal object VpnSystemStatusPolicy {
    fun observed(
        apiLevel: Int,
        serviceActive: Boolean,
        alwaysOn: Boolean,
        lockdown: Boolean,
        observedAt: Long,
    ): VpnSystemStatus = VpnSystemStatus(
        observed = true,
        serviceActive = serviceActive,
        alwaysOn = apiLevel >= SYSTEM_STATUS_API_LEVEL && alwaysOn,
        lockdown = apiLevel >= SYSTEM_STATUS_API_LEVEL && lockdown,
        observedAt = observedAt,
    )

    fun inactive(previous: VpnSystemStatus): VpnSystemStatus =
        if (previous.observed) previous.copy(serviceActive = false) else previous

    private const val SYSTEM_STATUS_API_LEVEL = 29
}
