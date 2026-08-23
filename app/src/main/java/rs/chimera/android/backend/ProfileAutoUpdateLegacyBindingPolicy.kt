package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProfileSummary

internal object ProfileAutoUpdateLegacyBindingPolicy {
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
