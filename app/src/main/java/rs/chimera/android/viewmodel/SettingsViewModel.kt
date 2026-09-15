package rs.chimera.android.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.ProxyProviderSnapshot
import rs.chimera.android.backend.model.RuleSnapshot
import rs.chimera.android.backend.model.SettingsDefaults
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.backend.model.VpnSystemStatus
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.AppearancePreference
import rs.chimera.android.ui.preferences.LanguagePreference
import rs.chimera.android.ui.preferences.UiVariant
import rs.chimera.android.util.toUserVisibleMessage

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val backend: ChimeraBackend = BackendProvider.provide()
    private val settingsUpdateMutex = Mutex()

    var languagePreference: LanguagePreference by mutableStateOf(AppPreferences.language(application))
        private set

    fun updateLanguagePreference(preference: LanguagePreference) {
        languagePreference = preference
        AppPreferences.updateLanguage(getApplication(), preference)
    }

    fun getLanguageDisplayName(): String {
        val context = getApplication<Application>().applicationContext
        return when (languagePreference) {
            LanguagePreference.SYSTEM -> context.getString(R.string.language_system)
            LanguagePreference.SIMPLIFIED_CHINESE -> {
                context.getString(R.string.language_simplified_chinese)
            }
            LanguagePreference.ENGLISH -> context.getString(R.string.language_english)
        }
    }

    var appearancePreference: AppearancePreference by mutableStateOf(
        AppPreferences.appearance(application),
    )
        private set

    fun updateAppearancePreference(preference: AppearancePreference) {
        appearancePreference = preference
        AppPreferences.updateAppearance(getApplication(), preference)
    }

    var uiVariant: UiVariant by mutableStateOf(AppPreferences.uiVariant(application))
        private set

    fun updateUiVariant(variant: UiVariant) {
        uiVariant = variant
        AppPreferences.updateUiVariant(getApplication(), variant)
    }

    var allowLan: Boolean by mutableStateOf(backend.settings.value.allowLan)
        private set

    var fakeIpEnabled: Boolean by mutableStateOf(backend.settings.value.fakeIp)
        private set

    var ipv6Enabled: Boolean by mutableStateOf(backend.settings.value.ipv6)
        private set

    var mixedPort: UShort by mutableStateOf(
        backend.settings.value.mixedPort,
    )
        private set

    var httpPort: UShort? by mutableStateOf(backend.settings.value.httpPort)
        private set

    var socksPort: UShort? by mutableStateOf(backend.settings.value.socksPort)
        private set

    var appFilterMode: AppFilterMode by mutableStateOf(
        AppFilterModePreference.parse(backend.settings.value.appFilterMode),
    )
    var runtimeSettingError: String? by mutableStateOf(null)
        private set
    var networkResetInProgress: Boolean by mutableStateOf(false)
        private set
    var allowedApps: Set<String> by mutableStateOf(backend.settings.value.allowedApps)
    var disallowedApps: Set<String> by mutableStateOf(backend.settings.value.disallowedApps)
    var vpnSystemStatus: VpnSystemStatus by mutableStateOf(backend.vpnSystemStatus.value)
        private set

    init {
        viewModelScope.launch {
            backend.settings.collect { reloadPersistedSettings() }
        }
        viewModelScope.launch {
            backend.vpnSystemStatus.collect { status -> vpnSystemStatus = status }
        }
    }

    fun updateAllowLan(enabled: Boolean) = updateRuntimeSetting(SettingsPatch(allowLan = enabled))

    fun updateFakeIpEnabled(enabled: Boolean) = updateRuntimeSetting(SettingsPatch(fakeIp = enabled))

    fun updateIpv6Enabled(enabled: Boolean) = updateRuntimeSetting(SettingsPatch(ipv6 = enabled))

    fun resetNetwork() {
        if (networkResetInProgress) return
        networkResetInProgress = true
        viewModelScope.launch {
            try {
                backend.resetNetwork()
                runtimeSettingError = null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                runtimeSettingError = error.toUserVisibleMessage(
                    getApplication(),
                    R.string.profile_unknown_error,
                )
            } finally {
                networkResetInProgress = false
            }
        }
    }

    fun resetRuntimeSettings() = updateRuntimeSetting(SettingsDefaults.resetPatch())

    fun updateListenerPorts(mixedPort: UShort, httpPort: UShort?, socksPort: UShort?) {
        updateRuntimeSetting(
            SettingsPatch(
                mixedPort = mixedPort,
                httpPort = httpPort,
                socksPort = socksPort,
                clearHttpPort = httpPort == null,
                clearSocksPort = socksPort == null,
            ),
        )
    }

    suspend fun saveAppFilter(
        mode: AppFilterMode,
        selectedApps: Set<String>,
    ) {
        val allowed = if (mode == AppFilterMode.ALLOWED) selectedApps else emptySet()
        val disallowed = if (mode == AppFilterMode.DISALLOWED) selectedApps else emptySet()
        settingsUpdateMutex.withLock {
            try {
                backend.updateSettings(
                    SettingsPatch(
                        appFilterMode = mode.name,
                        allowedApps = allowed,
                        disallowedApps = disallowed,
                    ),
                )
                reloadPersistedSettings()
            } catch (error: CancellationException) {
                reloadPersistedSettings()
                throw error
            } catch (error: Exception) {
                reloadPersistedSettings()
                throw error
            }
        }
    }

    suspend fun listRules(): List<RuleSnapshot> = backend.listRules()

    suspend fun listProxyProviders(): List<ProxyProviderSnapshot> = backend.listProxyProviders()

    suspend fun updateProxyProvider(name: String) {
        backend.updateProxyProvider(name)
    }

    suspend fun healthcheckProxyProvider(name: String) {
        backend.healthcheckProxyProvider(name)
    }

    suspend fun queryDns(name: String, recordType: String): String =
        backend.queryDns(name, recordType)

    fun dismissRuntimeSettingError() {
        runtimeSettingError = null
    }

    fun getAppFilterSummary(): String {
        val context = getApplication<Application>().applicationContext
        return when (appFilterMode) {
            AppFilterMode.ALL -> context.getString(R.string.app_selector_mode_all)
            AppFilterMode.ALLOWED -> context.getString(R.string.app_selector_selected, allowedApps.size)
            AppFilterMode.DISALLOWED -> context.getString(R.string.app_selector_selected, disallowedApps.size)
        }
    }

    private fun updateRuntimeSetting(
        patch: SettingsPatch,
    ) {
        viewModelScope.launch {
            settingsUpdateMutex.withLock {
                try {
                    backend.updateSettings(patch)
                    reloadPersistedSettings()
                    runtimeSettingError = null
                } catch (error: CancellationException) {
                    reloadPersistedSettings()
                    throw error
                } catch (error: Exception) {
                    reloadPersistedSettings()
                    runtimeSettingError = error.message.orEmpty()
                }
            }
        }
    }

    private fun reloadPersistedSettings() {
        val settings = backend.settings.value
        allowLan = settings.allowLan
        fakeIpEnabled = settings.fakeIp
        ipv6Enabled = settings.ipv6
        mixedPort = settings.mixedPort
        httpPort = settings.httpPort
        socksPort = settings.socksPort
        appFilterMode = AppFilterModePreference.parse(settings.appFilterMode)
        allowedApps = settings.allowedApps
        disallowedApps = settings.disallowedApps
    }
}
