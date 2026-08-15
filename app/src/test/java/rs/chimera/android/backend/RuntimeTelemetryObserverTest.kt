package rs.chimera.android.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ConnectionSnapshot
import rs.chimera.android.backend.model.ConnectionsSnapshot
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
            fetchConnections = {
                connectionCalls.incrementAndGet()
                ConnectionsSnapshot(emptyList(), 1, 2)
            },
            fetchMemory = { MemoryInfo(3, 4) },
            fetchProxyGroups = { emptyList() },
            recordError = { _, _, error -> throw AssertionError(error) },
            clearError = {},
            initialTrafficDelayMs = 0,
            pollIntervalMs = TEST_POLL_INTERVAL_MS,
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
    fun stoppingServiceClearsPublishedTelemetry() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.RUNNING)
        val appForeground = MutableStateFlow(true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val observer = RuntimeTelemetryObserver(
            scope = scope,
            serviceState = serviceState,
            appForeground = appForeground,
            fetchConnections = {
                ConnectionsSnapshot(
                    connections = listOf(testConnection()),
                    downloadTotal = 11,
                    uploadTotal = 12,
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
            pollIntervalMs = TEST_POLL_INTERVAL_MS,
        )

        try {
            observer.start()
            waitUntil {
                observer.traffic.value.connectionCount == 1 &&
                    observer.memoryInfo.value.inUse == 13L &&
                    observer.proxyGroups.value.size == 1
            }

            serviceState.value = ServiceState.STOPPED
            waitUntil {
                observer.traffic.value.connectionCount == 0 &&
                    observer.connections.value.connections.isEmpty() &&
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

    private fun testConnection(): ConnectionSnapshot =
        ConnectionSnapshot(
            id = "connection-1",
            host = "example.com",
            process = null,
            upload = 1,
            download = 2,
            startTime = 3,
            chains = emptyList(),
            rule = null,
            metadata = emptyMap(),
        )

    private companion object {
        const val TEST_POLL_INTERVAL_MS = 10L
        const val TEST_SETTLE_MS = 50L
        const val MAX_WAIT_ATTEMPTS = 100
    }
}
