package rs.chimera.android.viewmodel

import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.StartVpnResult
import uniffi.chimera_ffi.DelayHistory
import uniffi.chimera_ffi.MemoryResponse
import uniffi.chimera_ffi.Mode
import uniffi.chimera_ffi.Proxy

class HomeViewModel(
    private val backend: ChimeraBackend = BackendProvider.provide(),
) : ViewModel() {
    var isVpnRunning by mutableStateOf(false)
        private set

    var proxies by mutableStateOf(emptyArray<Proxy>())
        private set

    var isRefreshing by mutableStateOf(false)
        private set

    var currentMode by mutableStateOf(Mode.RULE)
        private set

    var isModeUpdating by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    val delays = mutableStateMapOf<String, String>()

    var memoryUsage by mutableStateOf<MemoryResponse?>(null)
        private set

    var connectionCount by mutableIntStateOf(0)
        private set

    var totalDownload by mutableLongStateOf(0)
        private set

    var totalUpload by mutableLongStateOf(0)
        private set

    init {
        observeBackend()
    }

    private fun observeBackend() {
        viewModelScope.launch {
            backend.serviceState.collectLatest { state ->
                isVpnRunning = state == ServiceState.RUNNING
                if (!isVpnRunning) {
                    proxies = emptyArray()
                    delays.clear()
                    currentMode = Mode.RULE
                    isModeUpdating = false
                }
            }
        }
        viewModelScope.launch {
            backend.proxyGroups.collectLatest(::applyProxyGroups)
        }
        viewModelScope.launch {
            backend.memoryInfo.collectLatest { memory ->
                memoryUsage = if (memory.inUse == 0L && memory.osLimit == 0L) {
                    null
                } else {
                    MemoryResponse(memory.inUse, memory.osLimit)
                }
            }
        }
        viewModelScope.launch {
            backend.traffic.collectLatest { traffic ->
                connectionCount = traffic.connectionCount
                totalDownload = traffic.downloadTotal
                totalUpload = traffic.uploadTotal
            }
        }
        viewModelScope.launch {
            backend.runtimeError.collectLatest { error ->
                errorMessage = error?.message
            }
        }
    }

    fun fetchProxies() {
        if (!isVpnRunning) {
            proxies = emptyArray()
            return
        }

        isRefreshing = true
        errorMessage = null
        viewModelScope.launch {
            try {
                applyProxyGroups(backend.listProxyGroups())
            } catch (error: Exception) {
                errorMessage = formatError("Failed to fetch proxies", error)
            } finally {
                isRefreshing = false
            }
        }
    }

    fun fetchMode() {
        currentMode = backend.proxyGroups.value.firstOrNull()?.mode ?: Mode.RULE
    }

    fun switchMode(mode: Mode) {
        if (!isVpnRunning || isModeUpdating || currentMode == mode) {
            return
        }

        viewModelScope.launch {
            val previousMode = currentMode
            isModeUpdating = true
            errorMessage = null
            try {
                backend.setMode(mode)
                currentMode = mode
                fetchProxies()
            } catch (error: Exception) {
                currentMode = previousMode
                errorMessage = formatError("Failed to switch proxy mode", error)
            } finally {
                isModeUpdating = false
            }
        }
    }

    fun testGroupDelay(proxyNames: List<String>) {
        viewModelScope.launch {
            errorMessage = null
            val failures = proxyNames.map { name ->
                async { testProxyDelay(name) }
            }.awaitAll().filterNotNull()
            failures.firstOrNull()?.let { error ->
                errorMessage = formatError("Failed to test proxy delay", error)
            }
        }
    }

    fun selectProxy(
        groupName: String,
        proxyName: String,
    ) {
        viewModelScope.launch {
            errorMessage = null
            try {
                backend.selectProxy(groupName, proxyName)
                fetchProxies()
            } catch (error: Exception) {
                errorMessage = formatError("Failed to select proxy", error)
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    fun startVpn(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>? = null) {
        viewModelScope.launch {
            when (val result = backend.prepareStartVpn()) {
                is StartVpnResult.Prepared -> launcher?.launch(result.intent)
                is StartVpnResult.PermissionNotRequired -> backend.startVpnAfterPermission()
                is StartVpnResult.Error -> errorMessage = result.message
            }
        }
    }

    fun stopVpn() {
        viewModelScope.launch {
            backend.stopVpn()
        }
    }

    private suspend fun testProxyDelay(name: String): Exception? {
        delays[name] = "testing..."
        return try {
            delays[name] = backend.testProxyDelay(name)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            delays[name] = "timeout"
            error
        }
    }

    private fun applyProxyGroups(groups: List<ProxyGroupSnapshot>) {
        currentMode = groups.firstOrNull()?.mode ?: Mode.RULE
        proxies = groups.map { group ->
            Proxy(
                name = group.name,
                proxyType = group.proxyDetails[group.name]?.type ?: "Selector",
                all = group.proxies,
                now = group.selected,
                history = group.proxyDetails[group.name]
                    ?.history
                    .orEmpty()
                    .map { DelayHistory(time = it.time.toString(), delay = it.delay) },
            )
        }.toTypedArray()
        groups.asSequence()
            .flatMap { it.proxyDetails.asSequence() }
            .forEach { (name, proxy) ->
                proxy.history.lastOrNull()?.delay?.takeIf { it > 0 }?.let { delay ->
                    delays[name] = "${delay}ms"
                }
            }
    }

    private fun formatError(
        prefix: String,
        error: Exception,
    ): String {
        val details = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        return "$prefix: $details"
    }
}
