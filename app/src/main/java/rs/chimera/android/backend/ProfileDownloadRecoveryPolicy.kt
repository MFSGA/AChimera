package rs.chimera.android.backend

import java.io.File
import java.util.UUID

internal object ProfileDownloadRecoveryPolicy {
    fun createStage(destinationFile: File): File {
        val parent = checkNotNull(destinationFile.parentFile) {
            "Profile update destination has no parent directory"
        }
        return File(parent, ".${destinationFile.name}.${UUID.randomUUID()}.download")
    }

    fun cleanup(directory: File) {
        directory.listFiles().orEmpty()
            .filter(::isManagedDownloadStage)
            .forEach { staged ->
                check(staged.delete()) {
                    "Failed to remove staged profile download: ${staged.name}"
                }
            }
    }

    private fun isManagedDownloadStage(file: File): Boolean {
        if (!file.isFile || !file.name.startsWith('.') || !file.name.endsWith(".download")) {
            return false
        }
        val stem = file.name.removePrefix(".").removeSuffix(".download")
        val separator = stem.lastIndexOf('.')
        if (separator <= 0) return false
        val uuid = stem.substring(separator + 1)
        return runCatching { UUID.fromString(uuid) }.isSuccess
    }
}
