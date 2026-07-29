package rs.chimera.android.viewmodel

import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ConnectionSnapshot
import rs.chimera.android.backend.model.ConnectionsSnapshot

internal data class ConnectionsUiState(
    val connections: List<ConnectionSnapshot> = emptyList(),
    val downloadTotal: Long = 0L,
    val uploadTotal: Long = 0L,
    val errorMessage: String? = null,
)

internal object ConnectionsStatePolicy {
    fun applySnapshot(
        current: ConnectionsUiState,
        snapshot: ConnectionsSnapshot,
    ): ConnectionsUiState =
        current.copy(
            connections = snapshot.connections,
            downloadTotal = snapshot.downloadTotal,
            uploadTotal = snapshot.uploadTotal,
        )

    fun applyRuntimeError(
        current: ConnectionsUiState,
        error: BackendRuntimeError?,
    ): ConnectionsUiState =
        current.copy(
            errorMessage = error
                ?.takeIf { it.source == BackendRuntimeErrorSource.TRAFFIC }
                ?.message,
        )

    fun applyFetchFailure(
        current: ConnectionsUiState,
        error: Exception,
        runtimeError: BackendRuntimeError?,
    ): ConnectionsUiState =
        current.copy(
            errorMessage = runtimeError
                ?.takeIf { it.source == BackendRuntimeErrorSource.TRAFFIC }
                ?.message
                ?: formatError("Failed to load connections", error),
        )

    private fun formatError(prefix: String, error: Exception): String {
        val details = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        return "$prefix: $details"
    }
}
