package rs.chimera.android.backend

import java.io.File

internal object ProfileFilePolicy {
    fun replaceAtomically(source: File, destination: File) {
        check(source.isFile) { "Replacement source does not exist: ${source.name}" }
        if (source.renameTo(destination)) return

        source.copyTo(destination, overwrite = true)
        check(source.delete()) {
            "Failed to remove replacement source: ${source.name}"
        }
    }

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
