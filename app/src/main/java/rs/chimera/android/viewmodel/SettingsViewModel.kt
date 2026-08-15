package rs.chimera.android.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.ProxyProviderSnapshot
import rs.chimera.android.backend.model.RuleSnapshot
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.service.PortPreference
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.AppearancePreference
import rs.chimera.android.ui.preferences.LanguagePreference
import rs.chimera.android.ui.preferences.UiVariant

class SettingsViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val backend: ChimeraBackend = BackendProvider.provide()

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

    var allowLan: Boolean by mutableStateOf(prefs.getBoolean("allow_lan", false))
        private set

    var fakeIpEnabled: Boolean by mutableStateOf(prefs.getBoolean("fake_ip", false))
        private set

    var ipv6Enabled: Boolean by mutableStateOf(prefs.getBoolean("ipv6", false))
        private set

    var mixedPort: UShort by mutableStateOf(
        PortPreference.parse(prefs.all["mixed_port"]) ?: DEFAULT_MIXED_PORT,
    )
        private set

    var httpPort: UShort? by mutableStateOf(PortPreference.parse(prefs.all["http_port"]))
        private set

    var socksPort: UShort? by mutableStateOf(PortPreference.parse(prefs.all["socks_port"]))
        private set

    var appFilterMode: AppFilterMode by mutableStateOf(
        AppFilterModePreference.parse(prefs.getString("app_filter_mode", null)),
    )
    var allowedApps: Set<String> by mutableStateOf(loadAppSet("allowed_apps"))
    var disallowedApps: Set<String> by mutableStateOf(loadAppSet("disallowed_apps"))

    fun updateAllowLan(enabled: Boolean) {
        updateRuntimeSetting(SettingsPatch(allowLan = enabled)) {
            allowLan = enabled
        }
    }

    fun updateFakeIpEnabled(enabled: Boolean) {
        updateRuntimeSetting(SettingsPatch(fakeIp = enabled)) {
            fakeIpEnabled = enabled
        }
    }

    fun updateIpv6Enabled(enabled: Boolean) {
        updateRuntimeSetting(SettingsPatch(ipv6 = enabled)) {
            ipv6Enabled = enabled
        }
    }

    fun updateListenerPorts(
        mixedPort: UShort,
        httpPort: UShort?,
        socksPort: UShort?,
    ) {
        updateRuntimeSetting(
            SettingsPatch(
                mixedPort = mixedPort,
                httpPort = httpPort,
                socksPort = socksPort,
                clearHttpPort = httpPort == null,
                clearSocksPort = socksPort == null,
            ),
        ) {
            this.mixedPort = mixedPort
            this.httpPort = httpPort
            this.socksPort = socksPort
        }
    }

    suspend fun saveAppFilter(
        mode: AppFilterMode,
        selectedApps: Set<String>,
    ) {
        val allowed = if (mode == AppFilterMode.ALLOWED) selectedApps else emptySet()
        val disallowed = if (mode == AppFilterMode.DISALLOWED) selectedApps else emptySet()
        backend.updateSettings(
            SettingsPatch(
                appFilterMode = mode.name,
                allowedApps = allowed,
                disallowedApps = disallowed,
            ),
        )
        appFilterMode = mode
        allowedApps = allowed
        disallowedApps = disallowed
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
        onUpdated: () -> Unit,
    ) {
        viewModelScope.launch {
            backend.updateSettings(patch)
            onUpdated()
        }
    }

    private fun loadAppSet(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    private companion object {
        val DEFAULT_MIXED_PORT: UShort = 7890u
    }
}
