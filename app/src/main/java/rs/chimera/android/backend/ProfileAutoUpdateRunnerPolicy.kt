package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import rs.chimera.android.backend.model.ProfileSummary

internal fun updateMatchesCurrentSource(
    profileId: String,
    sourceProfiles: List<ProfileSummary>,
    currentProfilesById: Map<String, ProfileSummary>,
): Boolean {
    val sourceProfile = sourceProfiles.firstOrNull { it.id == profileId } ?: return false
    val currentProfile = currentProfilesById[profileId] ?: return false
    return sourceProfile.hasSameAutoUpdateSource(currentProfile)
}

internal fun updateMatchesCurrentCommit(
    profileId: String,
    committedAt: Long,
    currentProfilesById: Map<String, ProfileSummary>,
): Boolean = currentProfilesById[profileId]?.lastUpdated?.let { it == committedAt } ?: true

internal fun shouldRetryFailedUpdate(
    attemptedProfile: ProfileSummary,
    currentProfile: ProfileSummary,
    now: Long,
): Boolean =
    if (attemptedProfile.hasSameAutoUpdateSource(currentProfile)) {
        ProfileAutoUpdatePolicy.shouldSchedule(listOf(currentProfile)) &&
            attemptedProfile.lastUpdated == currentProfile.lastUpdated
    } else {
        ProfileAutoUpdatePolicy.eligibleProfiles(listOf(currentProfile), now).isNotEmpty()
    }

internal fun ProfileSummary.hasSameAutoUpdateSource(other: ProfileSummary): Boolean =
    autoUpdate == other.autoUpdate &&
        url == other.url &&
        userAgent == other.userAgent &&
        proxyUrl == other.proxyUrl

internal fun ProfileSummary.toAutoUpdateState(runtimeApplyPending: Boolean): ProfileAutoUpdateState =
    ProfileAutoUpdateState(
        lastAttempt = lastAutoUpdateAttempt,
        failureCount = autoUpdateFailures,
        nextAttemptAt = nextAutoUpdateAt,
        lastError = lastAutoUpdateError,
        runtimeApplyPending = runtimeApplyPending,
        sourceFingerprint = ProfileAutoUpdatePolicy.sourceFingerprint(this),
        profileRevision = ProfileAutoUpdatePolicy.profileRevision(this),
    )

internal fun Throwable.throwIfCancellationOrFatal() {
    if (this !is Exception || this is CancellationException) throw this
}
