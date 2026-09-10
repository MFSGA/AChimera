package rs.chimera.android.settings

import rs.chimera.android.backend.model.SettingsDefaults
import rs.chimera.android.service.PortPreference

internal object RuntimeSettingsReader {
    fun read(values: Map<String, *>): RuntimeSettings {
        return RuntimeSettings(
            appFilterMode = (values["app_filter_mode"] as? String)
                ?.takeIf { it in setOf("ALL", "ALLOWED", "DISALLOWED") }
                ?: SettingsDefaults.APP_FILTER_MODE,
            allowedApps = (values["allowed_apps"] as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
            disallowedApps = (values["disallowed_apps"] as? Set<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
            allowLan = values["allow_lan"] as? Boolean ?: false,
            mixedPort = PortPreference.parse(values["mixed_port"]) ?: SettingsDefaults.MIXED_PORT,
            httpPort = PortPreference.parse(values["http_port"]),
            socksPort = PortPreference.parse(values["socks_port"]),
            fakeIp = values["fake_ip"] as? Boolean ?: false,
            ipv6 = values["ipv6"] as? Boolean ?: false,
        )
    }
}
