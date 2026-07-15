package rs.chimera.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.ConnectionSnapshot

class ConnectionsViewModel : ViewModel() {
    private val backend: ChimeraBackend = BackendProvider.provide()
    private var observationJob: Job? = null

    var connections by mutableStateOf<List<ConnectionSnapshot>>(emptyList())
        private set

    var downloadTotal by mutableLongStateOf(0L)
        private set

    var uploadTotal by mutableLongStateOf(0L)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun startPolling() {
        if (observationJob != null) {
            return
        }

        observationJob = viewModelScope.launch {
            backend.connections.collectLatest { snapshot ->
                errorMessage = null
                connections = snapshot.connections
                downloadTotal = snapshot.downloadTotal
                uploadTotal = snapshot.uploadTotal
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

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private fun formatError(
        prefix: String,
        error: Exception,
    ): String {
        val details = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        return "$prefix: $details"
    }

    private suspend fun fetchConnectionsInternal() {
        errorMessage = null
        try {
            val snapshot = backend.listConnections()
            connections = snapshot.connections
            downloadTotal = snapshot.downloadTotal
            uploadTotal = snapshot.uploadTotal
        } catch (error: Exception) {
            connections = emptyList()
            downloadTotal = 0L
            uploadTotal = 0L
            errorMessage = formatError("Failed to load connections", error)
        }
    }
}
