package rs.chimera.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.ConnectionSnapshot

class ConnectionsViewModel(
    private val backend: ChimeraBackend = BackendProvider.provide(),
) : ViewModel() {
    private var observationJob: Job? = null

    private var state = ConnectionsUiState()

    var connections by mutableStateOf<List<ConnectionSnapshot>>(state.connections)
        private set

    var downloadTotal by mutableLongStateOf(state.downloadTotal)
        private set

    var uploadTotal by mutableLongStateOf(state.uploadTotal)
        private set

    var errorMessage by mutableStateOf(state.errorMessage)
        private set

    var closingConnectionIds by mutableStateOf<Set<String>>(emptySet())
        private set

    var closeAllInProgress by mutableStateOf(false)
        private set

    fun startPolling() {
        if (observationJob != null) return

        observationJob = viewModelScope.launch {
            launch {
                backend.connections.collectLatest { snapshot ->
                    applyState(ConnectionsStatePolicy.applySnapshot(state, snapshot))
                }
            }
            launch {
                backend.runtimeError.collectLatest { error ->
                    applyState(ConnectionsStatePolicy.applyRuntimeError(state, error))
                }
            }
        }
    }

    fun stopPolling() {
        observationJob?.cancel()
        observationJob = null
    }

    fun fetchConnections() {
        viewModelScope.launch { fetchConnectionsInternal() }
    }

    fun closeConnection(id: String) {
        if (closeAllInProgress || id in closingConnectionIds) return
        closingConnectionIds = closingConnectionIds + id
        viewModelScope.launch {
            try {
                runCloseOperation { backend.closeConnection(id) }
            } finally {
                closingConnectionIds = closingConnectionIds - id
            }
        }
    }

    fun closeAllConnections() {
        if (closeAllInProgress || closingConnectionIds.isNotEmpty()) return
        closeAllInProgress = true
        viewModelScope.launch {
            try {
                runCloseOperation(backend::closeAllConnections)
            } finally {
                closeAllInProgress = false
            }
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private suspend fun fetchConnectionsInternal() {
        applyState(state.copy(errorMessage = null))
        try {
            val snapshot = backend.listConnections()
            applyState(ConnectionsStatePolicy.applySnapshot(state, snapshot))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            applyState(
                ConnectionsStatePolicy.applyFetchFailure(
                    current = state,
                    error = error,
                    runtimeError = backend.runtimeError.value,
                ),
            )
        }
    }

    private suspend fun runCloseOperation(operation: suspend () -> Unit) {
        applyState(state.copy(errorMessage = null))
        try {
            operation()
            fetchConnectionsInternal()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            applyState(
                ConnectionsStatePolicy.applyOperationFailure(
                    current = state,
                    error = error,
                    runtimeError = backend.runtimeError.value,
                ),
            )
        }
    }

    private fun applyState(newState: ConnectionsUiState) {
        state = newState
        connections = newState.connections
        downloadTotal = newState.downloadTotal
        uploadTotal = newState.uploadTotal
        errorMessage = newState.errorMessage
    }
}
