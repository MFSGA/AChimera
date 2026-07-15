package rs.chimera.android.backend.model

data class RemoteProfileRequest(
    val name: String?,
    val url: String,
    val autoUpdate: Boolean = false,
    val userAgent: String? = null,
    val proxyUrl: String? = null,
)
