package rs.chimera.android.settings

import rs.chimera.android.backend.model.SettingsDefaults

/** Persisted user preferences; the running core applies a snapshot when it starts. */
data class RuntimeSettings(
    val appFilterMode: String = SettingsDefaults.APP_FILTER_MODE,
    val allowedApps: Set<String> = emptySet(),
    val disallowedApps: Set<String> = emptySet(),
    val allowLan: Boolean = false,
    val mixedPort: UShort = SettingsDefaults.MIXED_PORT,
    val httpPort: UShort? = null,
    val socksPort: UShort? = null,
    val fakeIp: Boolean = false,
    val ipv6: Boolean = false,
)
