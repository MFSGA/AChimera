package rs.chimera.android.backend

import java.io.File
import java.util.UUID
import rs.chimera.android.util.runCatchingRecoverable

internal object ProfileBackupRecoveryPolicy {
    fun createBackupFile(destinationFile: File): File {
        val parent = checkNotNull(destinationFile.parentFile) {
            "Profile update destination has no parent directory"
        }
        val latestOrder = parent.listFiles()
            ?.mapNotNull(::managedBackup)
            ?.filter { it.targetName == destinationFile.name }
            ?.maxOfOrNull { it.order ?: it.file.lastModified() }
        val nextOrder = latestOrder?.let {
            check(it < Long.MAX_VALUE) { "Profile backup order is exhausted" }
            maxOf(System.currentTimeMillis(), it + 1)
        } ?: System.currentTimeMillis()
        return File(parent, ".${destinationFile.name}.${UUID.randomUUID()}.$nextOrder.backup")
    }

    fun recover(
        directory: File,
        pendingBackupNames: Set<String> = emptySet(),
        restore: (File, File) -> Unit = ProfileFilePolicy::replaceAtomically,
    ) {
        val files = directory.listFiles()
            ?: throw IllegalStateException("Failed to inspect staged profile backups")
        files
            .mapNotNull(::managedBackup)
            .groupBy { backup -> directory.resolve(backup.targetName).absolutePath }
            .values
            .forEach { backups ->
                val pendingBackup = backups
                    .filter { it.file.name in pendingBackupNames }
                    .maxWithOrNull(
                        compareBy<ManagedBackup> { it.order ?: it.file.lastModified() }
                            .thenBy { it.file.name },
                    )

                backups
                    .filterNot { it == pendingBackup }
                    .forEach { backup -> deleteRecoveredBackup(backup.file) }

                pendingBackup?.let { backup ->
                    restore(backup.file, directory.resolve(backup.targetName))
                }
            }
    }

    internal fun isManagedBackupName(name: String): Boolean = managedBackupName(name) != null

    private fun deleteRecoveredBackup(backup: File) {
        check(backup.delete()) {
            "Failed to remove superseded profile backup: ${backup.name}"
        }
    }

    private fun managedBackup(file: File): ManagedBackup? {
        if (!file.isFile) return null
        val parsed = managedBackupName(file.name) ?: return null
        return ManagedBackup(file, parsed.targetName, parsed.order)
    }

    internal fun managedBackupTargetName(name: String): String? = managedBackupName(name)?.targetName

    private fun managedBackupName(name: String): ParsedBackupName? {
        if (File(name).name != name || !name.startsWith('.') || !name.endsWith(".backup")) return null
        val stem = name.removePrefix(".").removeSuffix(".backup")
        val lastSeparator = stem.lastIndexOf('.')
        if (lastSeparator <= 0) return null
        val lastToken = stem.substring(lastSeparator + 1)
        val order = lastToken.toLongOrNull()
        val uuidSeparator = if (order != null) stem.lastIndexOf('.', lastSeparator - 1) else lastSeparator
        if (uuidSeparator <= 0) return null
        val targetName = stem.substring(0, uuidSeparator)
        val uuid = stem.substring(uuidSeparator + 1, if (order != null) lastSeparator else stem.length)
        if (targetName.isBlank() || runCatchingRecoverable { UUID.fromString(uuid) }.isFailure) return null
        return ParsedBackupName(targetName, order)
    }

    private data class ParsedBackupName(
        val targetName: String,
        val order: Long?,
    )

    private data class ManagedBackup(
        val file: File,
        val targetName: String,
        val order: Long?,
    )
}
