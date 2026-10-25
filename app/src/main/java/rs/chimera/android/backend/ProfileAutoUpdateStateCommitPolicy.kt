package rs.chimera.android.backend

internal object ProfileAutoUpdateStateCommitPolicy {
    fun canRecord(
        current: RemoteProfileCatalogEntry,
        state: ProfileAutoUpdateState,
    ): Boolean {
        if (!current.autoUpdate || current.url.isNullOrBlank()) return false
        val currentFingerprint = ProfileAutoUpdatePolicy.sourceFingerprint(
            autoUpdate = current.autoUpdate,
            url = current.url,
            userAgent = current.userAgent,
            proxyUrl = current.proxyUrl,
        )
        return state.sourceFingerprint == currentFingerprint &&
            state.intervalMillis == current.autoUpdateIntervalMillis &&
            state.profileRevision == (current.lastUpdated ?: 0L)
    }
}
