package rs.chimera.android.backend

import java.io.File
import java.util.UUID

internal object ProfileUpdateTransactionPolicy {
    suspend fun <T> run(
        destinationFile: File,
        update: suspend () -> File,
        persistMetadata: (File, File?) -> T,
        beginBackupTransaction: (File) -> Unit = {},
        clearBackupTransaction: (File) -> Unit = {},
        restore: (File, File) -> Unit = ProfileFilePolicy::replaceAtomically,
    ): T {
        val hadOriginal = destinationFile.isFile
        val backup = destinationFile.parentFile?.let { parent ->
            File(parent, ".${destinationFile.name}.${UUID.randomUUID()}.backup")
        }
        if (hadOriginal) {
            checkNotNull(backup) { "Profile update destination has no parent directory" }
            destinationFile.copyTo(backup, overwrite = false)
            try {
                beginBackupTransaction(backup)
            } catch (error: Throwable) {
                ProfileFilePolicy.deleteAfterFailure(backup, error)
                throw error
            }
        }

        val staged = try {
            update()
        } catch (error: Throwable) {
            backup?.let { transactionBackup ->
                ProfileFilePolicy.deleteAfterFailure(transactionBackup, error)
                if (!transactionBackup.exists()) {
                    runCatching { clearBackupTransaction(transactionBackup) }
                        .onFailure(error::addSuppressed)
                }
            }
            throw error
        }

        return try {
            ProfileFilePolicy.replaceAtomically(staged, destinationFile)
            persistMetadata(destinationFile, backup).also { backup?.delete() }
        } catch (error: Throwable) {
            if (staged.exists()) {
                runCatching { staged.delete() }
                    .onFailure(error::addSuppressed)
            }
            var rollbackSucceeded = false
            runCatching {
                if (hadOriginal) {
                    restore(checkNotNull(backup), destinationFile)
                } else {
                    check(!destinationFile.exists() || destinationFile.delete()) {
                        "Failed to remove uncommitted profile update: ${destinationFile.name}"
                    }
                }
            }.onSuccess {
                rollbackSucceeded = true
            }.onFailure(error::addSuppressed)
            if (backup != null && rollbackSucceeded) {
                runCatching { clearBackupTransaction(backup) }
                    .onFailure(error::addSuppressed)
            }
            throw error
        }
    }
}
