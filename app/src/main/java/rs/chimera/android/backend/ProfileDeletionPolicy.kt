package rs.chimera.android.backend

import java.io.File

internal object ProfileDeletionPolicy {
    fun delete(
        file: File,
        shouldDeleteFile: Boolean,
        persistDeletion: () -> Unit,
        rollbackCatalog: () -> Unit,
    ) {
        if (!shouldDeleteFile || !file.exists()) {
            persistDeletion()
            return
        }

        val staged = File(file.parentFile, ".${file.name}.deleting")
        check(!staged.exists() || staged.delete()) {
            "Failed to clear staged profile file: ${staged.name}"
        }
        check(file.renameTo(staged)) {
            "Failed to stage profile file deletion: ${file.name}"
        }

        try {
            persistDeletion()
        } catch (error: Exception) {
            restore(staged, file, error)
            throw error
        }

        if (staged.delete()) return

        val error = IllegalStateException("Failed to delete profile file: ${file.name}")
        try {
            rollbackCatalog()
        } catch (rollbackError: Exception) {
            error.addSuppressed(rollbackError)
        }
        restore(staged, file, error)
        throw error
    }

    private fun restore(
        staged: File,
        original: File,
        error: Exception,
    ) {
        if (staged.exists() && !staged.renameTo(original)) {
            error.addSuppressed(
                IllegalStateException("Failed to restore profile file: ${original.name}"),
            )
        }
    }
}
