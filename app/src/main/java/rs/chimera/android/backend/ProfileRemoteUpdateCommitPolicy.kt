package rs.chimera.android.backend

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import rs.chimera.android.util.runCatchingRecoverable

internal suspend fun completeRemoteProfileUpdateCommit(
    clearAutoUpdateState: () -> Boolean,
    synchronizeRuntimePath: () -> Unit,
    refreshActiveProfile: () -> Unit,
    afterCommit: suspend () -> Unit,
    onMaintenanceFailure: (Throwable) -> Unit,
) = withContext(NonCancellable) {
    runCatchingRecoverable(clearAutoUpdateState)
        .onSuccess { cleared ->
            if (!cleared) {
                onMaintenanceFailure(IllegalStateException("Failed to clear updated auto-update state"))
            }
        }
        .onFailure(onMaintenanceFailure)

    synchronizeRuntimePath()
    runCatchingRecoverable(refreshActiveProfile).onFailure(onMaintenanceFailure)
    afterCommit()
}
