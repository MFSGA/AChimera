package rs.chimera.android.ui

internal fun formatConnectionEndpoint(
    address: String,
    port: String,
    unknown: String = "?",
): String {
    val host = address.ifBlank { unknown }
    val normalizedHost = if (host.contains(':') && !(host.startsWith('[') && host.endsWith(']'))) {
        "[$host]"
    } else {
        host
    }
    return "$normalizedHost:${port.ifBlank { unknown }}"
}
