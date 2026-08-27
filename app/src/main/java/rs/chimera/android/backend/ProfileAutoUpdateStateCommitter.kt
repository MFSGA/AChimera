package rs.chimera.android.backend

internal suspend fun <T> commitAutoUpdateStateIfCurrent(
    state: ProfileAutoUpdateState,
    readCurrent: suspend () -> RemoteProfileCatalogEntry,
    commit: suspend () -> T,
): T? {
    val current = try {
        readCurrent()
    } catch (_: IllegalArgumentException) {
        return null
    }
    if (!ProfileAutoUpdateStateCommitPolicy.canRecord(current, state)) return null

    return commit()
}

internal suspend fun markRuntimeApplyPendingAndReadCurrent(
    state: ProfileAutoUpdateState,
    readCurrent: suspend () -> RemoteProfileCatalogEntry,
    markPending: suspend () -> Unit,
    readPending: suspend () -> Boolean,
): Boolean {
    commitAutoUpdateStateIfCurrent(
        state = state,
        readCurrent = readCurrent,
        commit = markPending,
    )
    return readPending()
}
