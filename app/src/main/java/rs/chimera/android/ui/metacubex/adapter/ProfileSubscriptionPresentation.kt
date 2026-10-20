package rs.chimera.android.ui.metacubex.adapter

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class ProfileSubscriptionPresentation(
    val usageText: String?,
    val expiresText: String?,
    val progressPermille: Int?,
)

internal fun profileSubscriptionPresentation(
    uploadBytes: Long?,
    downloadBytes: Long?,
    totalBytes: Long?,
    expiresAtMillis: Long?,
    locale: Locale = Locale.getDefault(),
): ProfileSubscriptionPresentation {
    val upload = uploadBytes?.coerceAtLeast(0L) ?: 0L
    val download = downloadBytes?.coerceAtLeast(0L) ?: 0L
    val used = if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
    val usageText = downloadBytes?.takeIf { it >= 2L }?.let {
        "${used.toBytesString(locale)}/${totalBytes?.takeIf { total -> total > 1L }?.toBytesString(locale).orEmpty()}"
    }
    val expiresText = expiresAtMillis?.takeIf { it > 0L }?.let {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", locale).format(Date(it))
    }
    val progress = totalBytes?.takeIf { it > 1L }?.let { total ->
        ((used.toDouble() / total.toDouble()) * PROGRESS_MAX)
            .toInt()
            .coerceIn(0, PROGRESS_MAX)
    }
    return ProfileSubscriptionPresentation(usageText, expiresText, progress)
}

private fun Long.toBytesString(locale: Locale): String {
    val units = arrayOf("Bytes", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
    var value = toDouble()
    var unitIndex = 0
    while (value > 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (unitIndex == 0) {
        "$this ${units[unitIndex]}"
    } else {
        String.format(locale, "%.2f %s", value, units[unitIndex])
    }
}

private const val PROGRESS_MAX = 1000
