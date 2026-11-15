package rs.chimera.android.backend.model

object SettingsDefaults {
    const val APP_FILTER_MODE: String = "ALL"
    const val ROUTE_SYSTEM_TRAFFIC: Boolean = true
    const val DNS_HIJACKING: Boolean = true
    const val SYSTEM_PROXY: Boolean = true
    const val ALLOW_BYPASS: Boolean = true
    const val BYPASS_PRIVATE_NETWORK: Boolean = true
    val MIXED_PORT: UShort = 7890u

    fun resetPatch(): SettingsPatch =
        SettingsPatch(
            routeSystemTraffic = ROUTE_SYSTEM_TRAFFIC,
            dnsHijacking = DNS_HIJACKING,
            systemProxy = SYSTEM_PROXY,
            allowLan = false,
            allowBypass = ALLOW_BYPASS,
            bypassPrivateNetwork = BYPASS_PRIVATE_NETWORK,
            mixedPort = MIXED_PORT,
            clearHttpPort = true,
            clearSocksPort = true,
            fakeIp = false,
            ipv6 = false,
            appFilterMode = APP_FILTER_MODE,
            allowedApps = emptySet(),
            disallowedApps = emptySet(),
        )
}
