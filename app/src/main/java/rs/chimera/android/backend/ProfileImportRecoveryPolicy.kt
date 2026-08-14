package rs.chimera.android.backend

import java.io.File
import java.util.UUID

internal object ProfileImportRecoveryPolicy {
    private const val STAGE_PREFIX = ".profile-import-"

    fun createStage(destinationFile: File): File {
        val parent = checkNotNull(destinationFile.parentFile) {
            "Profile import destination has no parent directory"
        }
        val extension = destinationFile.extension
            .takeIf { it.isNotBlank() }
            ?.let { ".$it" }
            .orEmpty()
        return File(parent, "$STAGE_PREFIX${UUID.randomUUID()}$extension")
    }

    fun recover(
        directory: File,
        referencedPaths: Set<String>,
        pendingDestinationNames: Set<String>,
    ) {
        val files = directory.listFiles()
            ?: throw IllegalStateException("Failed to inspect staged profile imports")
        files.filter(::isManagedStage).forEach { staged ->
            check(staged.delete()) {
                "Failed to remove staged profile import: ${staged.name}"
            }
        }

        pendingDestinationNames.forEach { destinationName ->
            require(destinationName.isNotBlank() && File(destinationName).name == destinationName) {
                "Invalid pending profile import destination"
            }
            val destination = directory.resolve(destinationName)
            if (destination.absolutePath !in referencedPaths && destination.exists()) {
                check(destination.delete()) {
                    "Failed to remove uncommitted profile import: ${destination.name}"
                }
            }
        }
    }

    private fun isManagedStage(file: File): Boolean {
        if (!file.isFile || !file.name.startsWith(STAGE_PREFIX)) return false
        val uuid = file.name.removePrefix(STAGE_PREFIX).substringBefore('.')
        return runCatching { UUID.fromString(uuid) }.isSuccess
    }
}
