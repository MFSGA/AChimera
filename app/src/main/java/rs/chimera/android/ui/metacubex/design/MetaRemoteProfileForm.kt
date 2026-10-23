package rs.chimera.android.ui.metacubex.design

internal data class MetaRemoteProfileForm(
    val name: String?,
    val url: String,
    val autoUpdate: Boolean,
    val userAgent: String?,
    val proxyUrl: String?,
)
