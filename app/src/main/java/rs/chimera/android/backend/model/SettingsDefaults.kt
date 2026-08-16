package rs.chimera.android.backend.model

object SettingsDefaults {
    const val APP_FILTER_MODE: String = "ALL"
    val MIXED_PORT: UShort = 7890u

    fun resetPatch(): SettingsPatch =
        SettingsPatch(
            allowLan = false,
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
