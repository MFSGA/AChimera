package rs.chimera.android.ui.metacubex.design

import java.util.Locale

/** Formats raw byte totals with the units and thresholds used by CMFA's main status card. */
internal fun formatMetaTrafficTotal(bytes: Long): String {
    val total = bytes.coerceAtLeast(0L)
    val locale = Locale.getDefault()
    val kib = 1024L
    val mib = kib * 1024L
    val gib = mib * 1024L

    return when {
        total > 100L * gib -> String.format(locale, "%.2f GiB", total.toDouble() / gib)
        total > 100L * mib -> String.format(locale, "%.2f MiB", total.toDouble() / mib)
        total > 100L * kib -> String.format(locale, "%.2f KiB", total.toDouble() / kib)
        else -> "$total Bytes"
    }
}
