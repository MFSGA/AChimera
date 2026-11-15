package rs.chimera.android.service

import rs.chimera.android.backend.model.VpnSystemStatus

internal object VpnSystemStatusPolicy {
    fun shouldRouteSystemTraffic(
        apiLevel: Int,
        configured: Boolean,
        lockdown: Boolean,
    ): Boolean = configured || (apiLevel >= SYSTEM_STATUS_API_LEVEL && lockdown)

    fun shouldRestartForSystemTrafficRouting(
        serviceActive: Boolean,
        tunnelActive: Boolean,
        shouldRouteSystemTraffic: Boolean,
    ): Boolean = serviceActive && !tunnelActive && shouldRouteSystemTraffic

    fun shouldRestartForSystemTrafficRoutingAfterStartup(
        serviceRunning: Boolean,
        tunnelActive: Boolean,
        shouldRouteSystemTraffic: Boolean,
    ): Boolean = serviceRunning && !tunnelActive && shouldRouteSystemTraffic

    fun observed(
        apiLevel: Int,
        serviceActive: Boolean,
        routesSystemTraffic: Boolean = serviceActive,
        alwaysOn: Boolean,
        lockdown: Boolean,
        observedAt: Long,
    ): VpnSystemStatus = VpnSystemStatus(
        observed = true,
        serviceActive = serviceActive,
        routesSystemTraffic = routesSystemTraffic,
        alwaysOn = apiLevel >= SYSTEM_STATUS_API_LEVEL && alwaysOn,
        lockdown = apiLevel >= SYSTEM_STATUS_API_LEVEL && lockdown,
        observedAt = observedAt,
    )

    private const val SYSTEM_STATUS_API_LEVEL = 29
}
