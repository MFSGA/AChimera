package rs.chimera.android.backend

import java.io.File

internal object ProfileFilePolicy {
    fun writeOrRollback(
        file: File,
        write: (File) -> Unit,
    ) {
        try {
            write(file)
        } catch (error: Throwable) {
            deleteAfterFailure(file, error)
            throw error
        }
    }

    fun <T> commitOrRollback(
        file: File,
        commit: () -> T,
    ): T =
        try {
            commit()
        } catch (error: Throwable) {
            deleteAfterFailure(file, error)
            throw error
        }

    fun deleteAfterFailure(file: File, originalError: Throwable) {
        runCatching {
            check(!file.exists() || file.delete()) {
                "Failed to remove incomplete profile file: ${file.name}"
            }
        }.onFailure(originalError::addSuppressed)
    }
}
