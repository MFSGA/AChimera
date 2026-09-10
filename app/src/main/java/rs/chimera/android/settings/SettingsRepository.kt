package rs.chimera.android.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.chimera.android.backend.model.SettingsPatch

interface SettingsRepository {
    val settings: StateFlow<RuntimeSettings>

    fun snapshot(): RuntimeSettings

    fun update(patch: SettingsPatch)
}

internal class PreferenceSettingsRepository(
    private val prefs: SharedPreferences,
) : SettingsRepository {
    private val mutableSettings = MutableStateFlow(read())
    override val settings = mutableSettings.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> snapshot() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    @Synchronized
    override fun snapshot(): RuntimeSettings = read().also { mutableSettings.value = it }

    @Synchronized
    override fun update(patch: SettingsPatch) {
        prefs.edit {
            patch.allowLan?.let { putBoolean("allow_lan", it) }
            patch.mixedPort?.let { putInt("mixed_port", it.toInt()) }
            if (patch.clearHttpPort) remove("http_port")
            patch.httpPort?.let { putInt("http_port", it.toInt()) }
            if (patch.clearSocksPort) remove("socks_port")
            patch.socksPort?.let { putInt("socks_port", it.toInt()) }
            patch.fakeIp?.let { putBoolean("fake_ip", it) }
            patch.ipv6?.let { putBoolean("ipv6", it) }
            patch.appFilterMode?.let { putString("app_filter_mode", it) }
            patch.allowedApps?.let { putStringSet("allowed_apps", it.toSet()) }
            patch.disallowedApps?.let { putStringSet("disallowed_apps", it.toSet()) }
        }
        snapshot()
    }

    private fun read(): RuntimeSettings = RuntimeSettingsReader.read(prefs.all)
}

/** Application-scoped composition point, shared by the backend and VPN service. */
internal object SettingsProvider {
    @Volatile
    private var repository: SettingsRepository? = null

    fun provide(context: Context): SettingsRepository = repository ?: synchronized(this) {
        repository ?: PreferenceSettingsRepository(
            context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE),
        ).also { repository = it }
    }
}
