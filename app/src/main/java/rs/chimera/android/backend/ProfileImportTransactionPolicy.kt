package rs.chimera.android.backend

import java.io.File

internal object ProfileImportTransactionPolicy {
    fun <T> run(
        stagedFile: File,
        destinationFile: File,
        beginImportTransaction: (File) -> Unit,
        persistMetadata: (File) -> T,
        clearImportTransaction: (File) -> Unit,
    ): T {
        check(stagedFile.isFile) { "Staged profile import does not exist: ${stagedFile.name}" }
        check(!destinationFile.exists()) { "Profile import destination already exists: ${destinationFile.name}" }

        try {
            beginImportTransaction(destinationFile)
        } catch (error: Throwable) {
            ProfileFilePolicy.deleteAfterFailure(stagedFile, error)
            throw error
        }

        return try {
            ProfileFilePolicy.replaceAtomically(stagedFile, destinationFile)
            persistMetadata(destinationFile)
        } catch (error: Throwable) {
            ProfileFilePolicy.deleteAfterFailure(stagedFile, error)
            ProfileFilePolicy.deleteAfterFailure(destinationFile, error)
            if (!stagedFile.exists() && !destinationFile.exists()) {
                runCatching { clearImportTransaction(destinationFile) }
                    .onFailure(error::addSuppressed)
            }
            throw error
        }
    }
}
