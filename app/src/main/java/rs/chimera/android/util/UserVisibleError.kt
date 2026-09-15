package rs.chimera.android.util

import android.content.Context
import androidx.annotation.StringRes
import rs.chimera.android.service.RuntimeLogSanitizer

internal fun Throwable.toUserVisibleMessage(
    context: Context,
    @StringRes fallbackRes: Int,
): String =
    sanitizeUserVisibleErrorText(
        value = message,
        fallback = context.getString(fallbackRes),
        privatePathPrefixes = listOf(context.applicationInfo.dataDir),
    )

internal fun sanitizeUserVisibleErrorText(
    value: String?,
    fallback: String,
    privatePathPrefixes: List<String> = emptyList(),
): String {
    val source = value?.takeIf(String::isNotBlank) ?: fallback
    val sanitized = privatePathPrefixes
        .asSequence()
        .filter(String::isNotBlank)
        .sortedByDescending(String::length)
        .fold(RuntimeLogSanitizer.sanitizeText(source)) { current, prefix ->
            current.replace(prefix, PRIVATE_PATH_LABEL)
        }
        .replace(WHITESPACE_PATTERN, " ")
        .trim()
        .ifBlank { fallback }

    return if (sanitized.length <= MAX_MESSAGE_CHARS) {
        sanitized
    } else {
        sanitized.take(MAX_MESSAGE_CHARS - 1) + "…"
    }
}

private const val PRIVATE_PATH_LABEL = "<app-private>"
private const val MAX_MESSAGE_CHARS = 300
private val WHITESPACE_PATTERN = Regex("\\s+")
