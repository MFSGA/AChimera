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
        if (currentSource != incomingSource || currentRevision > incomingRevision) return false
        if (currentRevision < incomingRevision) return true

        val currentAttempt = current.lastAttempt
        if (currentAttempt == null) {
            if (incoming.lastAttempt != null) return false
            val currentNextAttempt = current.nextAttemptAt ?: return true
            val incomingNextAttempt = incoming.nextAttemptAt ?: return false
            return currentNextAttempt <= incomingNextAttempt
        }
        val incomingAttempt = incoming.lastAttempt ?: return false
        if (current.runtimeApplyPending && !incoming.runtimeApplyPending) {
            return false
        }
        return currentAttempt <= incomingAttempt
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
