package rs.chimera.android.backend.model

import rs.chimera.android.backend.model.ProxyMode

data class ProxySnapshot(
    val name: String,
    val type: String,
    val history: List<ProxyDelayHistory>,
    val isGroup: Boolean = false,
    val now: String? = null,
    val title: String = name,
    val subtitle: String = type,
    val alive: Boolean = true,
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
