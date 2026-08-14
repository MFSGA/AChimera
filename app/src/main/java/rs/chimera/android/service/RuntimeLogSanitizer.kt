package rs.chimera.android.service

import java.io.File

internal object RuntimeLogSanitizer {
    fun profileLabel(path: String): String =
        File(path).name.takeIf { it.isNotBlank() } ?: "unknown-profile"
}
