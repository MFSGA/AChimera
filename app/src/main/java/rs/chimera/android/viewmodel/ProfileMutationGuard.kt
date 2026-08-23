package rs.chimera.android.viewmodel

import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType

internal fun Profile.matchesMutationSnapshot(current: ProfileSummary): Boolean =
    id == current.id &&
        name == current.name &&
        lastUpdated == current.lastUpdated &&
        (type == ProfileType.REMOTE) == current.isRemote &&
        url == current.url &&
        autoUpdate == current.autoUpdate &&
        userAgent == current.userAgent &&
        proxyUrl == current.proxyUrl

internal suspend fun ChimeraBackend.requireUnchangedProfile(profile: Profile): ProfileSummary {
    val current = listProfiles().firstOrNull { it.id == profile.id }
    if (current == null || !profile.matchesMutationSnapshot(current)) {
        throw ProfileChangedDuringMutationException()
    }
    return current
}

internal class ProfileChangedDuringMutationException : Exception()
