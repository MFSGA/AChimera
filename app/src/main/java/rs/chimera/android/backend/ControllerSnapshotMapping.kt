package rs.chimera.android.backend

import rs.chimera.android.backend.model.ConnectionSnapshot
import rs.chimera.android.backend.model.ConnectionsSnapshot
import rs.chimera.android.backend.model.ProxyDelayHistory
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxySnapshot
import uniffi.chimera_ffi.ConnectionsResponse
import uniffi.chimera_ffi.Mode
import uniffi.chimera_ffi.Proxy

internal fun List<Proxy>.toProxyGroupSnapshots(mode: Mode): List<ProxyGroupSnapshot> {
    if (mode == Mode.DIRECT) {
        val direct = ProxySnapshot(
            name = DIRECT_PROXY_NAME,
            type = DIRECT_PROXY_TYPE,
            history = emptyList(),
        )
        return listOf(
            ProxyGroupSnapshot(
                name = DIRECT_PROXY_NAME,
                proxies = emptyList(),
                selected = null,
                mode = mode,
                proxyDetails = mapOf(DIRECT_PROXY_NAME to direct),
            ),
        )
    }

    val proxiesByName = associateBy(Proxy::name)
    val globalGroup = proxiesByName[GLOBAL_PROXY_NAME]
    val orderedProxies = if (globalGroup == null) {
        sortedBy(Proxy::name)
    } else {
        buildList {
            val addedNames = mutableSetOf<String>()
            fun addOnce(proxy: Proxy) {
                if (addedNames.add(proxy.name)) add(proxy)
            }

            if (mode == Mode.GLOBAL) addOnce(globalGroup)
            globalGroup.all.forEach { name ->
                proxiesByName[name]?.let(::addOnce)
            }
            this@toProxyGroupSnapshots
                .asSequence()
                .filter { it.name != GLOBAL_PROXY_NAME }
                .sortedBy(Proxy::name)
                .forEach(::addOnce)
        }
    }
    val proxyDetails = orderedProxies.associate { proxy ->
        proxy.name to proxy.toSnapshot()
    }
    return orderedProxies.map { proxy ->
        ProxyGroupSnapshot(
            name = proxy.name,
            proxies = proxy.all,
            selected = proxy.now,
            mode = mode,
            proxyDetails = proxyDetails,
        )
    }
}

private fun Proxy.toSnapshot(): ProxySnapshot =
    ProxySnapshot(
        name = name,
        type = proxyType,
        history = history.map { entry ->
            ProxyDelayHistory(
                delay = entry.delay,
                time = entry.time.toLongOrNull() ?: 0L,
            )
        },
    )

private const val GLOBAL_PROXY_NAME = "GLOBAL"
private const val DIRECT_PROXY_NAME = "DIRECT"
private const val DIRECT_PROXY_TYPE = "Direct"

internal fun ConnectionsResponse.toConnectionsSnapshot(): ConnectionsSnapshot =
    ConnectionsSnapshot(
        connections = connections.map { connection ->
            ConnectionSnapshot(
                id = connection.id,
                host = connection.metadata.host,
                process = null,
                upload = connection.upload,
                download = connection.download,
                startTime = connection.start.toLongOrNull() ?: 0L,
                chains = connection.chains,
                rule = connection.rule,
                metadata = mapOf(
                    "network" to connection.metadata.network,
                    "type" to connection.metadata.metadataType,
                    "sourceIp" to connection.metadata.sourceIp,
                    "destinationIp" to connection.metadata.destinationIp.orEmpty(),
                    "sourcePort" to connection.metadata.sourcePort?.toString().orEmpty(),
                    "destinationPort" to connection.metadata.destinationPort.toString(),
                ),
            )
        },
        downloadTotal = downloadTotal,
        uploadTotal = uploadTotal,
    )
