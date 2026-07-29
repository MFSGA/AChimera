package rs.chimera.android.backend

import java.io.File

internal object ProfileDeletionRecoveryPolicy {
    private const val STAGED_SUFFIX = ".deleting"

    fun recover(
        directory: File,
        referencedPaths: Set<String>,
    ) {
        val stagedFiles = directory.listFiles { file ->
            file.name.startsWith('.') && file.name.endsWith(STAGED_SUFFIX)
        } ?: throw IllegalStateException("Failed to inspect staged profile files")

        stagedFiles.forEach { staged ->
            val originalName = staged.name
                .removePrefix(".")
                .removeSuffix(STAGED_SUFFIX)
                .takeIf { it.isNotBlank() }
                ?: return@forEach
            val original = File(directory, originalName)

            if (original.absolutePath in referencedPaths && !original.exists()) {
                check(staged.renameTo(original)) {
                    "Failed to restore staged profile file: $originalName"
                }
            } else {
                check(staged.delete()) {
                    "Failed to clear staged profile file: ${staged.name}"
                }
            }
        }
    }
}
