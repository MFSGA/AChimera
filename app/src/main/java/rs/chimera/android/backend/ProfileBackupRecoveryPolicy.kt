package rs.chimera.android.backend

import java.io.File
import java.util.UUID

internal object ProfileBackupRecoveryPolicy {
    fun recover(
        directory: File,
        pendingBackupNames: Set<String> = emptySet(),
        restore: (File, File) -> Unit = ProfileFilePolicy::replaceAtomically,
    ) {
        directory.listFiles().orEmpty()
            .mapNotNull { backup -> managedBackupTarget(backup)?.let { backup to it } }
            .forEach { (backup, target) ->
                if (backup.name in pendingBackupNames) {
                    restore(backup, target)
                } else {
                    check(backup.delete()) {
                        "Failed to remove committed profile backup: ${backup.name}"
                    }
                }
            }
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
