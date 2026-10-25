package rs.chimera.android.backend.model

object SettingsDefaults {
    const val APP_FILTER_MODE: String = "ALL"
    const val ALLOW_BYPASS: Boolean = true
    val MIXED_PORT: UShort = 7890u

    fun resetPatch(): SettingsPatch =
        SettingsPatch(
            allowLan = false,
            allowBypass = ALLOW_BYPASS,
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
