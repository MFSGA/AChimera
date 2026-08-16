package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ServiceState
import uniffi.chimera_ffi.Mode
import java.util.concurrent.atomic.AtomicInteger

class RuntimeTelemetryObserverTest {
    @Test
    fun pollingPausesWhileAppIsBackgrounded() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.RUNNING)
        val appForeground = MutableStateFlow(false)
        val connectionCalls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val observer = RuntimeTelemetryObserver(
            scope = scope,
            serviceState = serviceState,
            appForeground = appForeground,
            fetchTraffic = {
                connectionCalls.incrementAndGet()
                rs.chimera.android.backend.model.TrafficSnapshot(1, 2, 0)
            },
            fetchMemory = { MemoryInfo(3, 4) },
            fetchProxyGroups = { emptyList() },
            recordError = { _, _, error -> throw AssertionError(error) },
            clearError = {},
            initialTrafficDelayMs = 0,
            trafficPollIntervalMs = TEST_POLL_INTERVAL_MS,
            memoryPollIntervalMs = TEST_POLL_INTERVAL_MS,
            proxyGroupPollIntervalMs = TEST_POLL_INTERVAL_MS,
        )

        try {
            observer.start()
            delay(TEST_SETTLE_MS)
            assertEquals(0, connectionCalls.get())

            appForeground.value = true
            waitUntil { connectionCalls.get() > 0 }

            appForeground.value = false
            delay(TEST_SETTLE_MS)
            val callsAfterBackground = connectionCalls.get()
            delay(TEST_SETTLE_MS)
            assertEquals(callsAfterBackground, connectionCalls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startIsIdempotent() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.RUNNING)
        val appForeground = MutableStateFlow(true)
        val trafficCalls = AtomicInteger()
        val memoryCalls = AtomicInteger()
        val proxyCalls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val observer = RuntimeTelemetryObserver(
            scope = scope,
            serviceState = serviceState,
            appForeground = appForeground,
            fetchTraffic = {
                trafficCalls.incrementAndGet()
                rs.chimera.android.backend.model.TrafficSnapshot(1, 2, 0)
            },
            fetchMemory = {
                memoryCalls.incrementAndGet()
                MemoryInfo(3, 4)
            },
            fetchProxyGroups = {
                proxyCalls.incrementAndGet()
                emptyList()
            },
            recordError = { _, _, error -> throw AssertionError(error) },
            clearError = {},
            initialTrafficDelayMs = 0,
            trafficPollIntervalMs = TEST_SLOW_POLL_INTERVAL_MS,
            memoryPollIntervalMs = TEST_SLOW_POLL_INTERVAL_MS,
            proxyGroupPollIntervalMs = TEST_SLOW_POLL_INTERVAL_MS,
        )

        try {
            observer.start()
            observer.start()
            waitUntil { trafficCalls.get() > 0 && memoryCalls.get() > 0 && proxyCalls.get() > 0 }
            delay(TEST_SETTLE_MS)

            assertEquals(1, trafficCalls.get())
            assertEquals(1, memoryCalls.get())
            assertEquals(1, proxyCalls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun highFrequencyTrafficDoesNotForceLowFrequencyPolls() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.RUNNING)
        val appForeground = MutableStateFlow(true)
        val trafficCalls = AtomicInteger()
        val memoryCalls = AtomicInteger()
        val proxyCalls = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val observer = RuntimeTelemetryObserver(
            scope = scope,
            serviceState = serviceState,
            appForeground = appForeground,
            fetchTraffic = {
                trafficCalls.incrementAndGet()
                rs.chimera.android.backend.model.TrafficSnapshot(1, 2, 0)
            },
            fetchMemory = {
                memoryCalls.incrementAndGet()
                MemoryInfo(3, 4)
            },
            fetchProxyGroups = {
                proxyCalls.incrementAndGet()
                emptyList()
            },
            recordError = { _, _, error -> throw AssertionError(error) },
            clearError = {},
            initialTrafficDelayMs = 0,
            trafficPollIntervalMs = TEST_POLL_INTERVAL_MS,
            memoryPollIntervalMs = TEST_SLOW_POLL_INTERVAL_MS,
            proxyGroupPollIntervalMs = TEST_SLOW_POLL_INTERVAL_MS,
        )

        try {
            observer.start()
            waitUntil { trafficCalls.get() >= 3 }
            assertEquals(1, memoryCalls.get())
            assertEquals(1, proxyCalls.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun fetchCancellationDoesNotPublishRuntimeErrors() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.RUNNING)
        val appForeground = MutableStateFlow(true)
        val recordedErrors = AtomicInteger()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val observer = RuntimeTelemetryObserver(
            scope = scope,
            serviceState = serviceState,
            appForeground = appForeground,
            fetchTraffic = { throw CancellationException("traffic cancelled") },
            fetchMemory = { throw CancellationException("memory cancelled") },
            fetchProxyGroups = { throw CancellationException("proxies cancelled") },
            recordError = { _, _, _ -> recordedErrors.incrementAndGet() },
            clearError = {},
            initialTrafficDelayMs = 0,
            trafficPollIntervalMs = TEST_POLL_INTERVAL_MS,
            memoryPollIntervalMs = TEST_POLL_INTERVAL_MS,
            proxyGroupPollIntervalMs = TEST_POLL_INTERVAL_MS,
        )

        try {
            observer.start()
            delay(TEST_SETTLE_MS)
            assertEquals(0, recordedErrors.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun stoppingServiceClearsPublishedTelemetry() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.RUNNING)
        val appForeground = MutableStateFlow(true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val observer = RuntimeTelemetryObserver(
            scope = scope,
            serviceState = serviceState,
            appForeground = appForeground,
            fetchTraffic = {
                rs.chimera.android.backend.model.TrafficSnapshot(
                    downloadTotal = 11,
                    uploadTotal = 12,
                    connectionCount = 7,
                )
            },
            fetchMemory = { MemoryInfo(13, 14) },
            fetchProxyGroups = {
                listOf(
                    ProxyGroupSnapshot(
                        name = "AUTO",
                        proxies = emptyList(),
                        selected = null,
                        mode = Mode.RULE,
                        proxyDetails = emptyMap(),
                    ),
                )
            },
            recordError = { _, _, error -> throw AssertionError(error) },
            clearError = {},
            initialTrafficDelayMs = 0,
            trafficPollIntervalMs = TEST_POLL_INTERVAL_MS,
            memoryPollIntervalMs = TEST_POLL_INTERVAL_MS,
            proxyGroupPollIntervalMs = TEST_POLL_INTERVAL_MS,
        )

        try {
            observer.start()
            waitUntil {
                observer.traffic.value.connectionCount == 7 &&
                    observer.memoryInfo.value.inUse == 13L &&
                    observer.proxyGroups.value.size == 1
            }

            serviceState.value = ServiceState.STOPPED
            waitUntil {
                observer.traffic.value.connectionCount == 0 &&
                    observer.memoryInfo.value.inUse == 0L &&
                    observer.proxyGroups.value.isEmpty()
            }
        } finally {
            scope.cancel()
        }
    }

    private suspend fun waitUntil(condition: () -> Boolean) {
        repeat(MAX_WAIT_ATTEMPTS) {
            if (condition()) return
            delay(TEST_POLL_INTERVAL_MS)
        }
        throw AssertionError("Condition was not met")
    }

    private companion object {
        const val TEST_POLL_INTERVAL_MS = 10L
        const val TEST_SLOW_POLL_INTERVAL_MS = 1_000L
        const val TEST_SETTLE_MS = 50L
        const val MAX_WAIT_ATTEMPTS = 100
    }
}
