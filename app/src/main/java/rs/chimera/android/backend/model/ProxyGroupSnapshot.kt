package rs.chimera.android.backend.model

import uniffi.chimera_ffi.Mode
import uniffi.chimera_ffi.Proxy

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
    val mode: Mode,
    val proxyDetails: Map<String, ProxySnapshot>,
)