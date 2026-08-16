package rs.chimera.android.backend

import java.net.URI
import java.util.Locale
import rs.chimera.android.backend.model.RemoteProfileRequest

internal object ProfileRemotePolicy {
    fun isValidUrl(value: String): Boolean = parseHttpUrl(value) != null

    fun normalizeRequest(request: RemoteProfileRequest): RemoteProfileRequest =
        request.copy(
            name = request.name?.trim()?.takeIf { it.isNotEmpty() },
            url = requireValidUrl(request.url).toString(),
            userAgent = request.userAgent?.trim()?.takeIf { it.isNotEmpty() },
            proxyUrl = normalizeProxyUrl(request.proxyUrl),
        )

    fun isValidProxyUrl(value: String): Boolean = parseHttpUrl(value) != null

    fun requireValidProxyUrl(value: String): URI =
        parseHttpUrl(value) ?: throw IllegalArgumentException(
            "Profile proxy URL must use http or https",
        )

    fun invalidatesAutoUpdateState(
        current: RemoteProfileCatalogEntry,
        updated: RemoteProfileSettings,
    ): Boolean =
        current.url != updated.url ||
            current.autoUpdate != updated.autoUpdate ||
            current.userAgent != updated.userAgent ||
            current.proxyUrl != updated.proxyUrl

    fun requireValidUrl(value: String): URI =
        parseHttpUrl(value) ?: throw IllegalArgumentException(
            "Remote profile URL must use http or https",
        )

    fun storageFileName(profileId: String, sourceName: String): String {
        val extension = sourceName
            .substringAfterLast('.', "yaml")
            .replace(Regex("[^A-Za-z0-9]"), "")
            .lowercase(Locale.ROOT)
            .ifBlank { "yaml" }
        return "$profileId.$extension"
    }

    fun storageFileNameForUrl(profileId: String, value: String): String {
        val uri = requireValidUrl(value)
        val sourceName = uri.path
            ?.substringAfterLast('/')
            .orEmpty()
        return storageFileName(profileId, sourceName)
    }

    private fun normalizeProxyUrl(value: String?): String? =
        value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { requireValidProxyUrl(it).toString() }

    private fun parseHttpUrl(value: String): URI? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        return uri.takeIf {
            scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
        }
    }
}
