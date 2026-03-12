package rs.chimera.android.viewmodel

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import rs.chimera.android.Global
import rs.chimera.android.service.TunService
import rs.chimera.android.service.tunService
import uniffi.chimera_ffi.ClashController
import uniffi.chimera_ffi.MemoryResponse
import uniffi.chimera_ffi.Mode
import uniffi.chimera_ffi.Proxy
import uniffi.chimera_ffi.shutdown

class HomeViewModel : ViewModel() {
    var profilePath = MutableLiveData<String?>(null)

    var isVpnRunning by mutableStateOf(tunService != null)
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

    private val controller by lazy { ClashController("${Global.application.cacheDir}/clash.sock") }
    private var statsPollingJob: Job? = null
    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == PROFILE_PATH_KEY) {
                val path = sharedPreferences.getString(PROFILE_PATH_KEY, null)
                profilePath.value = path
                Global.restoreProfilePath()
            }
        }

    init {
        val context = Global.application.applicationContext
        val sharedPreferences = context.getSharedPreferences(FILE_PREFS, MODE_PRIVATE)
        val initialPath = sharedPreferences.getString(PROFILE_PATH_KEY, null)
        profilePath.value = initialPath
        Global.restoreProfilePath()
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

        viewModelScope.launch {
            Global.isServiceRunning.collectLatest { running ->
                isVpnRunning = running
                errorMessage = null
                statsPollingJob?.cancel()
                if (running) {
                    delay(1000)
                    fetchMode()
                    fetchProxies()
                    startStatsPolling()
                } else {
                    proxies = emptyArray()
                    delays.clear()
                    currentMode = Mode.RULE
                    isModeUpdating = false
                    memoryUsage = null
                    connectionCount = 0
                    totalDownload = 0
                    totalUpload = 0
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        statsPollingJob?.cancel()
        Global.application
            .getSharedPreferences(FILE_PREFS, MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    private fun startStatsPolling() {
        statsPollingJob?.cancel()
        statsPollingJob = viewModelScope.launch {
            while (isVpnRunning) {
                fetchOverviewStats()
                delay(3000)
            }
        }
    }

    private suspend fun fetchOverviewStats() {
        if (!isVpnRunning) {
            return
        }

        try {
            memoryUsage = controller.getMemory()
            val connectionResponse = controller.getConnections()
            connectionCount = connectionResponse.connections.size
            totalDownload = connectionResponse.downloadTotal
            totalUpload = connectionResponse.uploadTotal
        } catch (error: Exception) {
            errorMessage = formatError("Failed to fetch stats", error)
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

    fun startVpn(launcher: ManagedActivityResultLauncher<Intent, ActivityResult>? = null) {
        val app = Global.application
        if (Global.profilePath.isBlank()) {
            errorMessage = "Please select a config file first"
            return
        }

        val intent = VpnService.prepare(app)
        if (intent != null) {
            launcher?.launch(intent)
        } else {
            app.startService(Intent(app, TunService::class.java))
        }
    }

    fun stopVpn() {
        runCatching { shutdown() }
            .onFailure { error ->
                val details = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                errorMessage = "Failed to stop core: $details"
            }
        tunService?.stopVpn()
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

    private companion object {
        const val FILE_PREFS = "file_prefs"
        const val PROFILE_PATH_KEY = "profile_path"
    }
}
