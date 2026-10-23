package rs.chimera.android.backend

import java.io.File
import java.util.regex.Pattern

internal data class ProxySubtitlePatternMetadata(
    val lastModifiedMillis: Long,
    val length: Long,
)

/** Reads and compiles CMFA's optional proxy-title pattern once per active config revision. */
internal class ProxySubtitlePatternProvider(
    private val readPattern: (String) -> String?,
    private val readMetadata: (String) -> ProxySubtitlePatternMetadata? = { path ->
        File(path).takeIf { it.isFile }?.let { file ->
            ProxySubtitlePatternMetadata(file.lastModified(), file.length())
        }
    },
) {
    private data class CacheKey(
        val path: String,
        val metadata: ProxySubtitlePatternMetadata?,
    )

    private var cachedKey: CacheKey? = null
    private var cachedPattern: Pattern? = null

    @Synchronized
    fun patternFor(configPath: String): Pattern? {
        val metadata = configPath.takeIf(String::isNotBlank)?.let(readMetadata)
        val key = CacheKey(configPath, metadata)
        if (key == cachedKey) return cachedPattern

        cachedKey = key
        cachedPattern = if (metadata == null) {
            null
        } else {
            try {
                readPattern(configPath)?.let { pattern ->
                    Pattern.compile(pattern, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                }
            } catch (_: Exception) {
                null
            }
        }
        return cachedPattern
    }
}

internal data class ProxyDisplayText(
    val title: String,
    val subtitle: String,
)

internal fun formatProxyDisplayText(
    name: String,
    type: String,
    pattern: Pattern?,
): ProxyDisplayText {
    if (pattern == null) return ProxyDisplayText(name, type)
    val matcher = pattern.matcher(name)
    if (!matcher.find()) return ProxyDisplayText(name, type)

    return ProxyDisplayText(
        title = (name.substring(0, matcher.start()) + name.substring(matcher.end())).trim(),
        subtitle = name.substring(matcher.start(), matcher.end()).trim(),
    )
}
