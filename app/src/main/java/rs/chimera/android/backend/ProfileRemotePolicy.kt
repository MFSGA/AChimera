package rs.chimera.android.backend

import java.net.URI
import java.util.Locale

internal object ProfileRemotePolicy {
    fun isValidUrl(value: String): Boolean = parseUrl(value) != null

    fun requireValidUrl(value: String): URI =
        parseUrl(value) ?: throw IllegalArgumentException(
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

    private fun parseUrl(value: String): URI? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        return uri.takeIf {
            scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
        }
    }
}
