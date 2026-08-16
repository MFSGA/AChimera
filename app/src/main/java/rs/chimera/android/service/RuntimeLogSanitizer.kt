package rs.chimera.android.service

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal object RuntimeLogSanitizer {
    fun profileLabel(path: String): String =
        File(path).name.takeIf { it.isNotBlank() } ?: "unknown-profile"

    fun sanitizeText(value: String): String =
        SENSITIVE_ASSIGNMENT_PATTERN.replace(
            URL_PATTERN.replace(
                AUTHORIZATION_HEADER_PATTERN.replace(value) { match ->
                    "${match.groupValues[1]}: ***"
                },
            ) { match ->
                sanitizeUrl(match.value)
            },
        ) { match ->
            val quote = match.groupValues[1]
            val key = match.groupValues[2]
            val separator = match.groupValues[3]
            val valueQuote = match.groupValues[4]
            "$quote$key$quote$separator$valueQuote***$valueQuote"
        }

    private fun sanitizeUrl(rawValue: String): String {
        val suffix = rawValue.takeLastWhile { it in TRAILING_URL_PUNCTUATION }
        val rawUrl = rawValue.dropLast(suffix.length)
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return rawValue
        val authority = uri.rawAuthority ?: return rawValue
        val sanitizedAuthority = if (uri.rawUserInfo != null) {
            "***:***@${authority.substringAfterLast('@')}"
        } else {
            authority
        }
        val sanitizedQuery = uri.rawQuery?.split('&')?.joinToString("&") { parameter ->
            val rawKey = parameter.substringBefore('=')
            if (isSensitiveKey(rawKey)) "$rawKey=***" else parameter
        }

        return buildString {
            append(uri.scheme)
            append("://")
            append(sanitizedAuthority)
            append(uri.rawPath.orEmpty())
            sanitizedQuery?.let {
                append('?')
                append(it)
            }
            if (uri.rawFragment != null) {
                append("#***")
            }
            append(suffix)
        }
    }

    private fun isSensitiveKey(rawKey: String): Boolean {
        val decoded = runCatching {
            URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name())
        }.getOrDefault(rawKey)
        val normalized = decoded.lowercase().filter(Char::isLetterOrDigit)
        return normalized in SENSITIVE_KEYS
    }

    private val AUTHORIZATION_HEADER_PATTERN = Regex(
        pattern = """(?im)\b(proxy-authorization|authorization)\s*:\s*[^\r\n]+""",
    )
    private val URL_PATTERN = Regex(
        pattern = """(?i)\b(?:https?|socks5h?|socks)://[^\s"'<>]+""",
    )
    private val SENSITIVE_ASSIGNMENT_PATTERN = Regex(
        pattern =
            """(?i)(["']?)(token|access[_-]?token|api[_-]?key|password|passwd|secret|authorization|proxy[_-]?authorization)\1(\s*[:=]\s*)(["']?)([^"'\s,;&}]+)\4""",
    )
    private val SENSITIVE_KEYS = setOf(
        "token",
        "accesstoken",
        "apikey",
        "password",
        "passwd",
        "secret",
        "auth",
        "authorization",
        "proxyauthorization",
        "signature",
        "sig",
    )
    private const val TRAILING_URL_PUNCTUATION = ".,);"
}
