package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProfileSummary

internal object ProfileAutoUpdateLegacyBindingPolicy {
    fun readCurrentCatalogBestEffort(
        read: () -> RemoteProfileCatalogEntry,
    ): RemoteProfileCatalogEntry? =
        try {
            read()
        } catch (_: Exception) {
            null
        }

    fun persistBestEffort(
        boundState: ProfileAutoUpdateState,
        persist: () -> ProfileAutoUpdateState?,
    ): ProfileAutoUpdateState? =
        try {
            persist()
        } catch (_: Exception) {
            boundState
        }

    fun resolveCurrentStateAfterRejectedBinding(
        profile: ProfileSummary,
        currentState: ProfileAutoUpdateState,
    ): ProfileAutoUpdateState? =
        currentState.takeIf { ProfileAutoUpdatePolicy.stateMatchesSource(profile, it) }

    fun snapshotStillMatchesCurrentCatalog(
        snapshot: ProfileSummary,
        current: RemoteProfileCatalogEntry,
    ): Boolean =
        snapshot.isRemote &&
            current.type == "REMOTE" &&
            snapshot.url == current.url &&
            snapshot.autoUpdate == current.autoUpdate &&
            snapshot.userAgent == current.userAgent &&
            snapshot.proxyUrl == current.proxyUrl &&
            snapshot.lastUpdated == current.lastUpdated
}
