package rs.chimera.android.backend

import java.io.File

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
        val backup = if (hadOriginal) {
            ProfileBackupRecoveryPolicy.createBackupFile(destinationFile)
        } else {
            null
        }
        if (hadOriginal) {
            checkNotNull(backup)
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
                    try {
                        clearBackupTransaction(transactionBackup)
                    } catch (cleanupError: Exception) {
                        error.addSuppressed(cleanupError)
                    }
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
            try {
                if (hadOriginal) {
                    restore(checkNotNull(backup), destinationFile)
                } else {
                    check(!destinationFile.exists() || destinationFile.delete()) {
                        "Failed to remove uncommitted profile update: ${destinationFile.name}"
                    }
                }
                rollbackSucceeded = true
            } catch (rollbackError: Exception) {
                error.addSuppressed(rollbackError)
            }
            if (backup != null && rollbackSucceeded) {
                try {
                    clearBackupTransaction(backup)
                } catch (cleanupError: Exception) {
                    error.addSuppressed(cleanupError)
                }
            }
            throw error
        }
    }
}
