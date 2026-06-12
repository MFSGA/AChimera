package rs.chimera.android.backend.model

data class SettingsPatch(
    val allowLan: Boolean? = null,
    val mixedPort: UShort? = null,
    val httpPort: UShort? = null,
    val socksPort: UShort? = null,
    val fakeIp: Boolean? = null,
    val ipv6: Boolean? = null,
    val appFilterMode: String? = null,
    val allowedApps: Set<String>? = null,
    val disallowedApps: Set<String>? = null,
)