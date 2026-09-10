package rs.chimera.android.backend.model

import rs.chimera.android.backend.model.ProxyMode

data class ProxySnapshot(
    val name: String,
    val type: String,
    val history: List<ProxyDelayHistory>,
)

data class ProxyDelayHistory(
    val delay: Int,
    val time: Long,
)

data class ProxyGroupSnapshot(
    val name: String,
    val proxies: List<String>,
    val selected: String?,
    val mode: ProxyMode,
    val proxyDetails: Map<String, ProxySnapshot>,
)
