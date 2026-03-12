package rs.chimera.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rs.chimera.android.Global
import uniffi.chimera_ffi.ClashController
import uniffi.chimera_ffi.Connection

class ConnectionsViewModel : ViewModel() {
    private val controller by lazy { ClashController("${Global.application.cacheDir}/clash.sock") }
    private var pollingJob: Job? = null
    private val fetchMutex = Mutex()

    var connections by mutableStateOf<List<Connection>>(emptyList())
        private set

    var downloadTotal by mutableLongStateOf(0L)
        private set

    var uploadTotal by mutableLongStateOf(0L)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun startPolling() {
        if (pollingJob != null) {
            return
        }

        pollingJob = viewModelScope.launch {
            while (isActive) {
                fetchConnectionsInternal()
                delay(2000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
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
        fetchMutex.withLock {
            errorMessage = null
            try {
                val response = controller.getConnections()
                connections = response.connections
                downloadTotal = response.downloadTotal
                uploadTotal = response.uploadTotal
            } catch (error: Exception) {
                connections = emptyList()
                errorMessage = formatError("Failed to load connections", error)
            }
        }
    }
}
