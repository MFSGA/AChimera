package rs.chimera.android.backend.model

data class RemoteProfileSettings(
    val name: String,
    val url: String,
    val autoUpdate: Boolean,
    val userAgent: String? = null,
    val proxyUrl: String? = null,
)
