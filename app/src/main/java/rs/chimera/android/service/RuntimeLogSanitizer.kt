package rs.chimera.android.service

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import rs.chimera.android.util.runCatchingRecoverable

internal object RuntimeLogSanitizer {
    fun profileLabel(path: String): String =
        File(path).name.takeIf { it.isNotBlank() } ?: "unknown-profile"

    fun sanitizeText(value: String): String =
        SENSITIVE_ASSIGNMENT_PATTERN.replace(
            URL_PATTERN.replace(
                SENSITIVE_AUTH_ASSIGNMENT_PATTERN.replace(
                    SENSITIVE_HEADER_PATTERN.replace(value) { match ->
                        "${match.groupValues[1]}: ***"
                    },
                ) { match ->
                    "${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[1]}${match.groupValues[3]}***"
                }
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

    fun sanitizePrivatePaths(
        value: String,
        privatePathPrefixes: List<String>,
    ): String =
        privatePathPrefixes
            .asSequence()
            .map(String::trim)
            .map { it.trimEnd('/') }
            .filter(String::isNotBlank)
            .distinct()
            .sortedByDescending(String::length)
            .fold(sanitizeText(value)) { current, prefix ->
                current.replace(prefix, APP_PRIVATE_PATH_LABEL)
            }

    private fun sanitizeUrl(rawValue: String): String {
        val suffix = rawValue.takeLastWhile { it in TRAILING_URL_PUNCTUATION }
        val rawUrl = rawValue.dropLast(suffix.length)
        val uri = runCatchingRecoverable { URI(rawUrl) }.getOrNull()
            ?: return sanitizeMalformedUrl(rawUrl) + suffix
        val authority = uri.rawAuthority
            ?: return sanitizeMalformedUrl(rawUrl) + suffix
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

    private fun sanitizeMalformedUrl(rawUrl: String): String {
        val schemeEnd = rawUrl.indexOf("://")
        if (schemeEnd < 0) return rawUrl

        val authorityStart = schemeEnd + 3
        val authorityEnd = rawUrl.indexOfAny(charArrayOf('/', '?', '#'), authorityStart)
            .takeIf { it >= 0 }
            ?: rawUrl.length
        val authority = rawUrl.substring(authorityStart, authorityEnd)
        val sanitizedAuthority = if ('@' in authority) {
            "***:***@${authority.substringAfterLast('@')}"
        } else {
            authority
        }
        val queryStart = rawUrl.indexOf('?', authorityEnd)
        val fragmentStart = rawUrl.indexOf('#', authorityEnd)
        val pathEnd = listOf(queryStart, fragmentStart)
            .filter { it >= 0 }
            .minOrNull()
            ?: rawUrl.length
        val path = rawUrl.substring(authorityEnd, pathEnd)
        val query = if (queryStart >= 0) {
            val queryEnd = fragmentStart.takeIf { it > queryStart } ?: rawUrl.length
            rawUrl.substring(queryStart + 1, queryEnd)
                .split('&')
                .joinToString("&") { parameter ->
                    val rawKey = parameter.substringBefore('=')
                    if (isSensitiveKey(rawKey)) "$rawKey=***" else parameter
                }
        } else {
            null
        }

        return buildString {
            append(rawUrl.substring(0, authorityStart))
            append(sanitizedAuthority)
            append(path)
            query?.let {
                append('?')
                append(it)
            }
            if (fragmentStart >= 0) append("#***")
        }
    }

    private fun isSensitiveKey(rawKey: String): Boolean {
        val decoded = runCatchingRecoverable {
            URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name())
        }.getOrDefault(rawKey)
        val normalized = decoded.lowercase().filter(Char::isLetterOrDigit)
        return normalized in SENSITIVE_KEYS
    }

    private val SENSITIVE_HEADER_PATTERN = Regex(
        pattern =
            """(?im)\b(proxy-authorization|authorization|cookie|set-cookie|x-api-key|x-auth-token)\s*:\s*[^\r\n]+""",
    )
    private val URL_PATTERN = Regex(
        pattern = """(?i)\b(?:https?|socks5h?|socks)://[^\s"'<>]+""",
    )
    private val SENSITIVE_AUTH_ASSIGNMENT_PATTERN = Regex(
        pattern =
            """(?i)([\"']?)(authorization|proxy[_-]?authorization)\1(\s*[:=]\s*)(?:bearer|basic)\s+[^\s,;&}]+""",
    )
    private val SENSITIVE_ASSIGNMENT_PATTERN = Regex(
        pattern =
            """(?i)(["']?)(token|access[_-]?token|refresh[_-]?token|api[_-]?key|x[_-]?api[_-]?key|x[_-]?auth[_-]?token|cookie|set[_-]?cookie|session|session[_-]?id|password|passwd|secret|client[_-]?secret|authorization|proxy[_-]?authorization)\1(\s*[:=]\s*)(["']?)([^"'\s,;&}]+)\4""",
    )
    private val SENSITIVE_KEYS = setOf(
        "token",
        "accesstoken",
        "refreshtoken",
        "apikey",
        "xapikey",
        "xauthtoken",
        "cookie",
        "setcookie",
        "session",
        "sessionid",
        "password",
        "passwd",
        "secret",
        "clientsecret",
        "auth",
        "authorization",
        "proxyauthorization",
        "signature",
        "sig",
    )
    private const val APP_PRIVATE_PATH_LABEL = "<app-private>"
    private const val TRAILING_URL_PUNCTUATION = ".,);"
}
