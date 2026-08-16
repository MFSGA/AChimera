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
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.TrafficSnapshot
import rs.chimera.android.util.runCatchingPreservingCancellation

internal class RuntimeTelemetryObserver(
    private val scope: CoroutineScope,
    private val serviceState: StateFlow<ServiceState>,
    private val appForeground: StateFlow<Boolean>,
    private val fetchTraffic: suspend () -> TrafficSnapshot,
    private val fetchMemory: suspend () -> MemoryInfo,
    private val fetchProxyGroups: suspend () -> List<ProxyGroupSnapshot>,
    private val recordError: (BackendRuntimeErrorSource, String, Throwable) -> Unit,
    private val clearError: (BackendRuntimeErrorSource) -> Unit,
    private val initialTrafficDelayMs: Long = INITIAL_TRAFFIC_DELAY_MS,
    private val trafficPollIntervalMs: Long = TRAFFIC_POLL_INTERVAL_MS,
    private val memoryPollIntervalMs: Long = MEMORY_POLL_INTERVAL_MS,
    private val proxyGroupPollIntervalMs: Long = PROXY_GROUP_POLL_INTERVAL_MS,
) {
    private val _traffic = MutableStateFlow(TrafficSnapshot(0, 0, 0))
    val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    private val _memoryInfo = MutableStateFlow(MemoryInfo(0, 0))
    val memoryInfo: StateFlow<MemoryInfo> = _memoryInfo.asStateFlow()

    private val _proxyGroups = MutableStateFlow<List<ProxyGroupSnapshot>>(emptyList())
    val proxyGroups: StateFlow<List<ProxyGroupSnapshot>> = _proxyGroups.asStateFlow()

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
                        clearError(BackendRuntimeErrorSource.TRAFFIC)
                        return@collectLatest
                    }
                    if (!shouldPoll) return@collectLatest

                    delay(initialTrafficDelayMs)
                    while (true) {
                        runCatchingPreservingCancellation { fetchTraffic() }
                            .onSuccess { snapshot ->
                                clearError(BackendRuntimeErrorSource.TRAFFIC)
                                _traffic.value = snapshot
                            }.onFailure { error ->
                                recordError(
                                    BackendRuntimeErrorSource.TRAFFIC,
                                    "Failed to refresh connections",
                                    error,
                                )
                            }
                        delay(trafficPollIntervalMs)
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
                        delay(memoryPollIntervalMs)
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
                        delay(proxyGroupPollIntervalMs)
                    }
                }
        }
    }

    private companion object {
        const val INITIAL_TRAFFIC_DELAY_MS = 1_000L
        const val TRAFFIC_POLL_INTERVAL_MS = 3_000L
        const val MEMORY_POLL_INTERVAL_MS = 10_000L
        const val PROXY_GROUP_POLL_INTERVAL_MS = 10_000L
    }
}
