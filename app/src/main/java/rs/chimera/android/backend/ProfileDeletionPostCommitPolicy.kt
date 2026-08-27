package rs.chimera.android.backend

import rs.chimera.android.util.runCatchingRecoverable

internal fun completeProfileDeletionPostCommit(
    activePath: String?,
    updateRuntimePath: (String?) -> Unit,
    clearAutoUpdateState: () -> Boolean,
    refreshActiveProfile: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    updateRuntimePath(activePath)

    runCatchingRecoverable(clearAutoUpdateState)
        .onSuccess { cleared ->
            if (!cleared) {
                onFailure(IllegalStateException("Failed to clear deleted auto-update state"))
            }
        }
        .onFailure(onFailure)

    runCatchingRecoverable(refreshActiveProfile).onFailure(onFailure)
}
