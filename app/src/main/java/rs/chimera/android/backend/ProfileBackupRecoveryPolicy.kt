package rs.chimera.android.backend

import java.io.File
import java.util.UUID

internal data class ProfileBackupRecoveryMetadata(
    val fileSize: Long,
    val lastUpdated: Long?,
)

internal object ProfileBackupRecoveryPolicy {
    fun recover(
        directory: File,
        metadataByPath: Map<String, ProfileBackupRecoveryMetadata>,
        pendingBackupNames: Set<String> = emptySet(),
        restore: (File, File) -> Unit = ProfileFilePolicy::replaceAtomically,
    ) {
        directory.listFiles().orEmpty()
            .mapNotNull { backup -> managedBackupTarget(backup)?.let { backup to it } }
            .forEach { (backup, target) ->
                if (backup.name in pendingBackupNames) {
                    restore(backup, target)
                    return@forEach
                }

                val metadata = metadataByPath[target.absolutePath] ?: return@forEach
                when {
                    shouldRestore(backup, target, metadata) -> restore(backup, target)
                    shouldDiscard(backup, target, metadata) -> check(backup.delete()) {
                        "Failed to remove committed profile backup: ${backup.name}"
                    }
                }
            }
    }

    private fun shouldRestore(
        backup: File,
        target: File,
        metadata: ProfileBackupRecoveryMetadata,
    ): Boolean {
        if (!target.isFile) return backup.length() == metadata.fileSize
        val backupMatchesCatalog = backup.length() == metadata.fileSize
        if (!backupMatchesCatalog) return false
        val lastUpdated = metadata.lastUpdated
        return target.length() != metadata.fileSize ||
            (lastUpdated != null && target.lastModified() > lastUpdated)
    }

    private fun shouldDiscard(
        backup: File,
        target: File,
        metadata: ProfileBackupRecoveryMetadata,
    ): Boolean {
        if (!target.isFile || target.length() != metadata.fileSize) return false
        val lastUpdated = metadata.lastUpdated
        return backup.length() != metadata.fileSize ||
            (lastUpdated != null && target.lastModified() <= lastUpdated)
    }

    private fun managedBackupTarget(file: File): File? {
        if (!file.isFile || !file.name.startsWith('.') || !file.name.endsWith(".backup")) return null
        val stem = file.name.removePrefix(".").removeSuffix(".backup")
        val separator = stem.lastIndexOf('.')
        if (separator <= 0) return null
        val targetName = stem.substring(0, separator)
        val uuid = stem.substring(separator + 1)
        if (runCatching { UUID.fromString(uuid) }.isFailure) return null
        return file.parentFile?.resolve(targetName)
    }
}
