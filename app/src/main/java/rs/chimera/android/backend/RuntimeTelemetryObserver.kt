package rs.chimera.android.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ConnectionsSnapshot
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.TrafficSnapshot
import rs.chimera.android.util.runCatchingPreservingCancellation

internal class RuntimeTelemetryObserver(
    private val scope: CoroutineScope,
    private val serviceState: StateFlow<ServiceState>,
    private val appForeground: StateFlow<Boolean>,
    private val fetchConnections: suspend () -> ConnectionsSnapshot,
    private val fetchMemory: suspend () -> MemoryInfo,
    private val fetchProxyGroups: suspend () -> List<ProxyGroupSnapshot>,
    private val recordError: (BackendRuntimeErrorSource, String, Throwable) -> Unit,
    private val clearError: (BackendRuntimeErrorSource) -> Unit,
    private val initialTrafficDelayMs: Long = INITIAL_TRAFFIC_DELAY_MS,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
) {
    private val _traffic = MutableStateFlow(TrafficSnapshot(0, 0, 0))
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    private val _memoryInfo = MutableStateFlow(MemoryInfo(0, 0))
    val memoryInfo: StateFlow<MemoryInfo> = _memoryInfo.asStateFlow()

    private val _proxyGroups = MutableStateFlow<List<ProxyGroupSnapshot>>(emptyList())
    val proxyGroups: StateFlow<List<ProxyGroupSnapshot>> = _proxyGroups.asStateFlow()

    private val _connections = MutableStateFlow(ConnectionsSnapshot(emptyList(), 0, 0))
    val connections: StateFlow<ConnectionsSnapshot> = _connections.asStateFlow()

    fun start() {
        observeTraffic()
        observeMemory()
        observeProxyGroups()
    }

    private fun observeTraffic() {
        scope.launch {
            combine(serviceState, appForeground, ::shouldPollRuntimeTelemetry)
                .collectLatest { shouldPoll ->
                    if (serviceState.value != ServiceState.RUNNING) {
                        _traffic.value = TrafficSnapshot(0, 0, 0)
                        _connections.value = ConnectionsSnapshot(emptyList(), 0, 0)
                        clearError(BackendRuntimeErrorSource.TRAFFIC)
                        return@collectLatest
                    }
                    if (!shouldPoll) return@collectLatest

                    delay(initialTrafficDelayMs)
                    while (true) {
                        runCatchingPreservingCancellation { fetchConnections() }
                            .onSuccess { snapshot ->
                                clearError(BackendRuntimeErrorSource.TRAFFIC)
                                _traffic.value = TrafficSnapshot(
                                    downloadTotal = snapshot.downloadTotal,
                                    uploadTotal = snapshot.uploadTotal,
                                    connectionCount = snapshot.connections.size,
                                )
                                _connections.value = snapshot
                            }.onFailure { error ->
                                recordError(
                                    BackendRuntimeErrorSource.TRAFFIC,
                                    "Failed to refresh connections",
                                    error,
                                )
                            }
                        delay(pollIntervalMs)
                    }
                }
        }
    }

    private fun observeMemory() {
        scope.launch {
            combine(serviceState, appForeground, ::shouldPollRuntimeTelemetry)
                .collectLatest { shouldPoll ->
                    if (serviceState.value != ServiceState.RUNNING) {
                        _memoryInfo.value = MemoryInfo(0, 0)
                        clearError(BackendRuntimeErrorSource.MEMORY)
                        return@collectLatest
                    }
                    if (!shouldPoll) return@collectLatest

                    while (true) {
                        runCatchingPreservingCancellation { fetchMemory() }
                            .onSuccess { memory ->
                                clearError(BackendRuntimeErrorSource.MEMORY)
                                _memoryInfo.value = memory
                            }.onFailure { error ->
                                recordError(
                                    BackendRuntimeErrorSource.MEMORY,
                                    "Failed to refresh memory",
                                    error,
                                )
                            }
                        delay(pollIntervalMs)
                    }
                }
        }
    }

    private fun observeProxyGroups() {
        scope.launch {
            combine(serviceState, appForeground, ::shouldPollRuntimeTelemetry)
                .collectLatest { shouldPoll ->
                    if (serviceState.value != ServiceState.RUNNING) {
                        _proxyGroups.value = emptyList()
                        clearError(BackendRuntimeErrorSource.PROXY_GROUPS)
                        return@collectLatest
                    }
                    if (!shouldPoll) return@collectLatest

                    while (true) {
                        runCatchingPreservingCancellation { fetchProxyGroups() }
                            .onSuccess { groups ->
                                clearError(BackendRuntimeErrorSource.PROXY_GROUPS)
                                _proxyGroups.value = groups
                            }.onFailure { error ->
                                recordError(
                                    BackendRuntimeErrorSource.PROXY_GROUPS,
                                    "Failed to refresh proxy groups",
                                    error,
                                )
                            }
                        delay(pollIntervalMs)
                    }
                }
        }
    }

    private companion object {
        const val INITIAL_TRAFFIC_DELAY_MS = 1_000L
        const val POLL_INTERVAL_MS = 3_000L
    }
}
