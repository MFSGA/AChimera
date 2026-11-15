package rs.chimera.android.settings

import rs.chimera.android.backend.model.SettingsDefaults

/** Persisted user preferences; the running core applies a snapshot when it starts. */
data class RuntimeSettings(
    val routeSystemTraffic: Boolean = SettingsDefaults.ROUTE_SYSTEM_TRAFFIC,
    val dnsHijacking: Boolean = SettingsDefaults.DNS_HIJACKING,
    val systemProxy: Boolean = SettingsDefaults.SYSTEM_PROXY,
    val appFilterMode: String = SettingsDefaults.APP_FILTER_MODE,
    val allowedApps: Set<String> = emptySet(),
    val disallowedApps: Set<String> = emptySet(),
    val allowLan: Boolean = false,
    val allowBypass: Boolean = SettingsDefaults.ALLOW_BYPASS,
    val bypassPrivateNetwork: Boolean = SettingsDefaults.BYPASS_PRIVATE_NETWORK,
    val mixedPort: UShort = SettingsDefaults.MIXED_PORT,
    val httpPort: UShort? = null,
    val socksPort: UShort? = null,
    val fakeIp: Boolean = false,
    val ipv6: Boolean = false,
)
