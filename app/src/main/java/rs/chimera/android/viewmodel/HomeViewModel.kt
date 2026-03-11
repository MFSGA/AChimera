package rs.chimera.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rs.chimera.android.Global
import uniffi.chimera_ffi.ClashController
import uniffi.chimera_ffi.Mode
import uniffi.chimera_ffi.Proxy

class HomeViewModel : ViewModel() {
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

    private val controller by lazy { ClashController("${Global.application.cacheDir}/clash.sock") }

    init {
        viewModelScope.launch {
            Global.isServiceRunning.collectLatest { running ->
                isVpnRunning = running
                errorMessage = null
                if (running) {
                    delay(1000)
                    fetchMode()
                    fetchProxies()
                } else {
                    proxies = emptyArray()
                    delays.clear()
                    currentMode = Mode.RULE
                    isModeUpdating = false
                }
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
                val response = controller.getProxies()
                response.forEach { proxy ->
                    proxy.history.lastOrNull()?.delay?.takeIf { it > 0 }?.let { lastDelay ->
                        delays[proxy.name] = "${lastDelay}ms"
                    }
                }
                proxies = response.toTypedArray()
            } catch (error: Exception) {
                errorMessage = formatError("Failed to fetch proxies", error)
            } finally {
                isRefreshing = false
            }
        }
    }

    fun fetchMode() {
        if (!isVpnRunning) {
            currentMode = Mode.RULE
            return
        }

        viewModelScope.launch {
            try {
                currentMode = controller.getMode() ?: Mode.RULE
            } catch (error: Exception) {
                errorMessage = formatError("Failed to fetch mode", error)
            }
        }
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
                controller.setMode(mode)
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
            proxyNames.map { name ->
                async { testProxyDelay(name) }
            }.awaitAll()
        }
    }

    fun selectProxy(
        groupName: String,
        proxyName: String,
    ) {
        viewModelScope.launch {
            errorMessage = null
            try {
                controller.selectProxy(groupName, proxyName)
                fetchProxies()
            } catch (error: Exception) {
                errorMessage = formatError("Failed to select proxy", error)
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    private suspend fun testProxyDelay(name: String) {
        try {
            delays[name] = "testing..."
            val response = controller.getProxyDelay(name, null, null)
            delays[name] = "${response.delay}ms"
        } catch (_: Exception) {
            delays[name] = "timeout"
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
