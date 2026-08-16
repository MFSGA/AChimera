package rs.chimera.android.service

import android.content.Context
import android.content.SharedPreferences
import rs.chimera.android.ffi.ProfileOverride

internal data class TunServiceSettings(
    val appFilterMode: String,
    val allowedApps: Set<String>,
    val disallowedApps: Set<String>,
    val allowLan: Boolean,
    val mixedPort: UShort,
    val httpPort: UShort?,
    val socksPort: UShort?,
    val fakeIp: Boolean,
    val ipv6: Boolean,
)

internal object TunServiceSettingsLoader {
    fun load(context: Context): TunServiceSettings {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return TunServiceSettings(
            appFilterMode = prefs.getString("app_filter_mode", "ALL") ?: "ALL",
            allowedApps = prefs.getStringSet("allowed_apps", emptySet()) ?: emptySet(),
            disallowedApps = prefs.getStringSet("disallowed_apps", emptySet()) ?: emptySet(),
            allowLan = prefs.getBoolean("allow_lan", false),
            mixedPort = prefs.getPort("mixed_port", 7890u),
            httpPort = prefs.getOptionalPort("http_port"),
            socksPort = prefs.getOptionalPort("socks_port"),
            fakeIp = prefs.getBoolean("fake_ip", false),
            ipv6 = prefs.getBoolean("ipv6", false),
        )
    }

    fun createProfileOverride(
        currentTunFd: Int,
        settings: TunServiceSettings,
        logFilePath: String,
    ): ProfileOverride =
        ProfileOverride(
            tunFd = currentTunFd,
            logFilePath = logFilePath,
            allowLan = settings.allowLan,
            mixedPort = settings.mixedPort,
            httpPort = settings.httpPort,
            socksPort = settings.socksPort,
            fakeIp = settings.fakeIp,
            ipv6 = settings.ipv6,
        )
}

private fun SharedPreferences.getOptionalPort(key: String): UShort? =
    PortPreference.parse(all[key])

private fun SharedPreferences.getPort(
    key: String,
    defaultValue: UShort,
): UShort = getOptionalPort(key) ?: defaultValue
