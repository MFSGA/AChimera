package rs.chimera.android.backend

import rs.chimera.android.util.PrivacySafeLog
import java.io.File

internal class ProfileStartupRecovery(
    private val profileStagingStore: ProfileStagingStore,
    private val filesDir: File,
) {
    fun recover() {
        recoverStep("Failed to recover staged profile imports") {
            profileStagingStore.recoverImports()
        }
        recoverStep("Failed to recover staged profile backups") {
            profileStagingStore.recoverBackups()
        }
        recoverStep("Failed to recover staged profile deletions") {
            profileStagingStore.recoverDeletions()
        }
        recoverStep("Failed to recover staged profile downloads") {
            ProfileDownloadRecoveryPolicy.cleanup(filesDir)
        }
    }

    private inline fun recoverStep(
        message: String,
        action: () -> Unit,
    ) {
        runProfileStartupRecoveryStep(action)
            .onFailure { error -> PrivacySafeLog.error(TAG, message, error) }
    }

    private companion object {
        const val TAG = "ChimeraBackend"
    }
}

internal inline fun runProfileStartupRecoveryStep(action: () -> Unit): Result<Unit> =
    try {
        action()
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(error)
    }
