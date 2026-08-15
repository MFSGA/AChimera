package rs.chimera.android.backend.model

enum class SettingsApplyEffect {
    IMMEDIATE,
    RESTART_CORE,
    REBUILD_TUN,
}

data class SettingsPatch(
    val allowLan: Boolean? = null,
    val mixedPort: UShort? = null,
    val httpPort: UShort? = null,
    val socksPort: UShort? = null,
    val clearHttpPort: Boolean = false,
    val clearSocksPort: Boolean = false,
    val fakeIp: Boolean? = null,
    val ipv6: Boolean? = null,
    val appFilterMode: String? = null,
    val allowedApps: Set<String>? = null,
    val disallowedApps: Set<String>? = null,
) {
    fun requiredApplyEffect(): SettingsApplyEffect =
        when {
            ipv6 != null ||
                appFilterMode != null ||
                allowedApps != null ||
                disallowedApps != null -> SettingsApplyEffect.REBUILD_TUN
            allowLan != null ||
                mixedPort != null ||
                httpPort != null ||
                socksPort != null ||
                clearHttpPort ||
                clearSocksPort ||
                fakeIp != null -> SettingsApplyEffect.RESTART_CORE
            else -> SettingsApplyEffect.IMMEDIATE
        }
}
