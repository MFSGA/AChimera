package rs.chimera.android.backend.model

data class VpnSystemStatus(
    val observed: Boolean = false,
    val serviceActive: Boolean = false,
    val routesSystemTraffic: Boolean = false,
    val alwaysOn: Boolean = false,
    val lockdown: Boolean = false,
    val observedAt: Long = 0L,
)
