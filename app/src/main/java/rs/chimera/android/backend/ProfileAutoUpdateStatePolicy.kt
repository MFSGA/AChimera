package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import java.security.MessageDigest

internal data class ProfileAutoUpdateState(
    val lastAttempt: Long?,
    val failureCount: Int,
    val nextAttemptAt: Long?,
    val lastError: String?,
    val runtimeApplyPending: Boolean = false,
    val sourceFingerprint: String? = null,
    val profileRevision: Long? = null,
)

/** Selects scheduled remote refreshes and computes bounded retry backoff. */
internal object ProfileAutoUpdatePolicy {
    const val UPDATE_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L
    const val BASE_RETRY_DELAY_MILLIS = 15L * 60L * 1_000L
    const val MAX_RETRY_DELAY_MILLIS = 5L * 60L * 60L * 1_000L

    fun eligibleProfiles(
        profiles: List<ProfileSummary>,
        now: Long = System.currentTimeMillis(),
    ): List<ProfileSummary> =
        profiles.filter { profile ->
            val nextAttemptAt = profile.nextAutoUpdateAt
                ?: profile.lastUpdated?.let { it + UPDATE_INTERVAL_MILLIS }
            isConfigured(profile) &&
                !profile.runtimeApplyPending &&
                (nextAttemptAt == null || nextAttemptAt <= now)
        }

    fun shouldSchedule(profiles: List<ProfileSummary>): Boolean = profiles.any(::isConfigured)

    fun stateMatchesSource(profile: ProfileSummary, state: ProfileAutoUpdateState): Boolean =
        (state.sourceFingerprint == null || state.sourceFingerprint == sourceFingerprint(profile)) &&
            (state.profileRevision == null || state.profileRevision == profileRevision(profile))

    fun bindStateToSource(
        profile: ProfileSummary,
        state: ProfileAutoUpdateState,
        profileRevision: Long = profileRevision(profile),
    ): ProfileAutoUpdateState =
        state.copy(
            sourceFingerprint = sourceFingerprint(profile),
            profileRevision = profileRevision,
        )

    fun bindLegacyStateToCurrentProfile(
        profile: ProfileSummary,
        state: ProfileAutoUpdateState,
    ): ProfileAutoUpdateState =
        if (state.sourceFingerprint == null || state.profileRevision == null) {
            bindStateToSource(profile, state)
        } else {
            state
        }

    fun profileRevision(profile: ProfileSummary): Long = profile.lastUpdated ?: NO_PROFILE_REVISION

    fun successState(attemptedAt: Long): ProfileAutoUpdateState =
        ProfileAutoUpdateState(
            lastAttempt = attemptedAt,
            failureCount = 0,
            nextAttemptAt = attemptedAt + UPDATE_INTERVAL_MILLIS,
            lastError = null,
        )

    fun refreshNowState(now: Long): ProfileAutoUpdateState =
        ProfileAutoUpdateState(
            lastAttempt = null,
            failureCount = 0,
            nextAttemptAt = now,
            lastError = null,
        )

    fun shouldRetryAfterGuardedStateWrite(
        attemptedState: ProfileAutoUpdateState,
        currentState: ProfileAutoUpdateState?,
        fallbackRetry: Boolean,
        now: Long,
    ): Boolean {
        if (currentState == null || currentState == attemptedState) return fallbackRetry
        return currentState.runtimeApplyPending || currentState.nextAttemptAt?.let { it <= now } == true
    }

    fun failureState(
        previousFailures: Int,
        attemptedAt: Long,
        error: Throwable,
    ): ProfileAutoUpdateState {
        val failureCount = (previousFailures + 1).coerceAtLeast(1)
        val exponent = (failureCount - 1).coerceAtMost(MAX_BACKOFF_EXPONENT)
        val delay = (BASE_RETRY_DELAY_MILLIS * (1L shl exponent))
            .coerceAtMost(MAX_RETRY_DELAY_MILLIS)
        return ProfileAutoUpdateState(
            lastAttempt = attemptedAt,
            failureCount = failureCount,
            nextAttemptAt = attemptedAt + delay,
            lastError = error::class.java.simpleName.take(MAX_ERROR_LENGTH),
        )
    }

    private fun isConfigured(profile: ProfileSummary): Boolean =
        profile.type == ProfileType.REMOTE &&
            profile.isRemote &&
            profile.autoUpdate &&
            !profile.url.isNullOrBlank()

    fun sourceFingerprint(profile: ProfileSummary): String =
        sourceFingerprint(
            autoUpdate = profile.autoUpdate,
            url = profile.url,
            userAgent = profile.userAgent,
            proxyUrl = profile.proxyUrl,
        )

    fun sourceFingerprint(
        autoUpdate: Boolean,
        url: String?,
        userAgent: String?,
        proxyUrl: String?,
    ): String {
        val source = listOf(
            autoUpdate.toString(),
            url.orEmpty(),
            userAgent.orEmpty(),
            proxyUrl.orEmpty(),
        ).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private const val NO_PROFILE_REVISION = 0L
    private const val MAX_BACKOFF_EXPONENT = 16
    private const val MAX_ERROR_LENGTH = 80
}
