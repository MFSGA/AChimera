package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException

internal suspend fun completeRemoteImportPostCommit(
    restoreActiveProfile: suspend () -> Unit,
    synchronizeSchedule: suspend () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    runPostCommitStep(restoreActiveProfile, onFailure)
    runPostCommitStep(synchronizeSchedule, onFailure)
}

private suspend fun runPostCommitStep(
    action: suspend () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    try {
        action()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        onFailure(error)
    }
}
