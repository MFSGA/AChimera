package rs.chimera.android.backend

import rs.chimera.android.util.runCatchingRecoverable

internal fun completeProfileDeletionPostCommit(
    restoreActivePath: () -> Unit,
    clearAutoUpdateState: () -> Boolean,
    refreshActiveProfile: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    runCatchingRecoverable(restoreActivePath).onFailure(onFailure)

    runCatchingRecoverable(clearAutoUpdateState)
        .onSuccess { cleared ->
            if (!cleared) {
                onFailure(IllegalStateException("Failed to clear deleted auto-update state"))
            }
        }
        .onFailure(onFailure)

    runCatchingRecoverable(refreshActiveProfile).onFailure(onFailure)
}
