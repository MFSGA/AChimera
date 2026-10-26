package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

internal object ProfileImportTransactionPolicy {
    suspend fun <T> commit(block: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) { block() }
    }

    suspend fun completePostCommitBestEffort(
        afterCommit: suspend () -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        try {
            afterCommit()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
        }
    }

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
                try {
                    clearImportTransaction(destinationFile)
                } catch (cleanupError: Exception) {
                    error.addSuppressed(cleanupError)
                }
            }
            throw error
        }
    }
}
