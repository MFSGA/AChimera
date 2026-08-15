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
    val proxyDetails = associate { proxy ->
        proxy.name to ProxySnapshot(
            name = proxy.name,
            type = proxy.proxyType,
            history = proxy.history.map { history ->
                ProxyDelayHistory(
                    delay = history.delay,
                    time = history.time.toLongOrNull() ?: 0L,
                )
            },
        )
    }
    return map { proxy ->
        ProxyGroupSnapshot(
            name = proxy.name,
            proxies = proxy.all,
            selected = proxy.now,
            mode = mode,
            proxyDetails = proxyDetails,
        )
    }
}

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
