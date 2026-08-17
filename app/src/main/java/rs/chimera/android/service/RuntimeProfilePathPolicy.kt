package rs.chimera.android.service

import java.io.File

internal object RuntimeProfilePathPolicy {
    fun requireAvailable(
        rawPath: String,
        unavailableMessage: String,
    ): String {
        val path = rawPath.trim()
        require(path.isNotEmpty()) { unavailableMessage }

        val profile = File(path)
        require(profile.exists() && profile.isFile) { unavailableMessage }
        return path
    }
}
