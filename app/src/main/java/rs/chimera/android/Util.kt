package rs.chimera.android

import android.content.Intent
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow
import kotlin.reflect.KClass

fun formatSize(size: Long): String {
    if (size <= 0) {
        return "0 B"
    }

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format(
        Locale.US,
        "%.2f %s",
        size / 1024.0.pow(digitGroups.toDouble()),
        units[digitGroups],
    )
}

val KClass<*>.intent: Intent
    get() = Intent(Global.application, this.java)
