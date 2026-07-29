package rs.chimera.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ConnectionSnapshot
import rs.chimera.android.backend.model.ConnectionsSnapshot

class ConnectionsStatePolicyTest {
    @Test
    fun snapshotUpdatesDataWithoutClearingCurrentError() {
        val current = ConnectionsUiState(errorMessage = "temporary failure")

        val result = ConnectionsStatePolicy.applySnapshot(current, snapshot())

        assertEquals(listOf("connection-1"), result.connections.map { it.id })
        assertEquals(120L, result.downloadTotal)
        assertEquals(45L, result.uploadTotal)
        assertEquals("temporary failure", result.errorMessage)
    }

    @Test
    fun trafficErrorPreservesLastSuccessfulData() {
        val current = ConnectionsStatePolicy.applySnapshot(ConnectionsUiState(), snapshot())

        val result = ConnectionsStatePolicy.applyRuntimeError(
            current,
            BackendRuntimeError(BackendRuntimeErrorSource.TRAFFIC, "controller unavailable"),
        )

        assertEquals(current.connections, result.connections)
        assertEquals(current.downloadTotal, result.downloadTotal)
        assertEquals(current.uploadTotal, result.uploadTotal)
        assertEquals("controller unavailable", result.errorMessage)
    }

    @Test
    fun unrelatedRuntimeErrorDoesNotSurfaceOnConnections() {
        val result = ConnectionsStatePolicy.applyRuntimeError(
            ConnectionsUiState(errorMessage = "old error"),
            BackendRuntimeError(BackendRuntimeErrorSource.MEMORY, "memory unavailable"),
        )

        assertNull(result.errorMessage)
    }

    @Test
    fun clearingRuntimeErrorRestoresHealthyStateWithoutDroppingData() {
        val current = ConnectionsStatePolicy.applySnapshot(ConnectionsUiState(), snapshot())
            .copy(errorMessage = "controller unavailable")

        val result = ConnectionsStatePolicy.applyRuntimeError(current, null)

        assertEquals(current.connections, result.connections)
        assertNull(result.errorMessage)
    }

    @Test
    fun fetchFailurePrefersSharedTrafficError() {
        val result = ConnectionsStatePolicy.applyFetchFailure(
            current = ConnectionsStatePolicy.applySnapshot(ConnectionsUiState(), snapshot()),
            error = IllegalStateException("raw failure"),
            runtimeError = BackendRuntimeError(
                BackendRuntimeErrorSource.TRAFFIC,
                "Failed to refresh connections: socket closed",
            ),
        )

        assertEquals("Failed to refresh connections: socket closed", result.errorMessage)
        assertEquals(listOf("connection-1"), result.connections.map { it.id })
    }

    @Test
    fun fetchFailureFallsBackToExceptionDetails() {
        val result = ConnectionsStatePolicy.applyFetchFailure(
            current = ConnectionsUiState(),
            error = IllegalArgumentException("VPN is not running"),
            runtimeError = null,
        )

        assertEquals("Failed to load connections: VPN is not running", result.errorMessage)
    }

    private fun snapshot() =
        ConnectionsSnapshot(
            connections = listOf(
                ConnectionSnapshot(
                    id = "connection-1",
                    host = "example.com",
                    process = null,
                    upload = 10,
                    download = 20,
                    startTime = 1,
                    chains = listOf("Proxy"),
                    rule = "MATCH",
                    metadata = emptyMap(),
                ),
            ),
            downloadTotal = 120,
            uploadTotal = 45,
        )
}
