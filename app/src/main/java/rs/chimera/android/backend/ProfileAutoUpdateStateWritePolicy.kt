package rs.chimera.android.backend

internal object ProfileAutoUpdateStateWritePolicy {
    fun canReplace(
        current: ProfileAutoUpdateState,
        incoming: ProfileAutoUpdateState,
    ): Boolean {
        val incomingSource = incoming.sourceFingerprint ?: return false
        val incomingRevision = incoming.profileRevision ?: return false
        if (current.isEmptyUnboundState()) return true

        val currentSource = current.sourceFingerprint ?: return false
        val currentRevision = current.profileRevision ?: return false
        return currentSource == incomingSource && currentRevision <= incomingRevision
    }

    private fun ProfileAutoUpdateState.isEmptyUnboundState(): Boolean =
        lastAttempt == null &&
            failureCount == 0 &&
            nextAttemptAt == null &&
            lastError == null &&
            !runtimeApplyPending &&
            sourceFingerprint == null &&
            profileRevision == null
}
