package rs.chimera.android.backend

import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ServiceState

internal data class ProfileAutoUpdateState(
    val lastAttempt: Long?,
    val failureCount: Int,
    val nextAttemptAt: Long?,
    val lastError: String?,
)

/** Selects scheduled remote refreshes and computes bounded retry backoff. */
internal object ProfileAutoUpdatePolicy {
    const val BASE_RETRY_DELAY_MILLIS = 15L * 60L * 1_000L
    const val MAX_RETRY_DELAY_MILLIS = 5L * 60L * 60L * 1_000L

    fun eligibleProfiles(
        profiles: List<ProfileSummary>,
        now: Long = System.currentTimeMillis(),
    ): List<ProfileSummary> =
        profiles.filter { profile ->
            isConfigured(profile) &&
                (profile.nextAutoUpdateAt == null || profile.nextAutoUpdateAt <= now)
        }

    fun shouldSchedule(profiles: List<ProfileSummary>): Boolean = profiles.any(::isConfigured)

    fun successState(attemptedAt: Long): ProfileAutoUpdateState =
        ProfileAutoUpdateState(
            lastAttempt = attemptedAt,
            failureCount = 0,
            nextAttemptAt = null,
            lastError = null,
        )

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

    private const val MAX_BACKOFF_EXPONENT = 16
    private const val MAX_ERROR_LENGTH = 80
}

internal interface ProfileAutoUpdateOperations {
    val serviceState: StateFlow<ServiceState>

    suspend fun listProfiles(): List<ProfileSummary>

    suspend fun updateRemoteProfile(id: String)

    suspend fun recordAutoUpdateState(id: String, state: ProfileAutoUpdateState)

    suspend fun restartVpn()
}

internal data class ProfileAutoUpdateResult(
    val attempted: Int,
    val updated: Int,
    val deferred: Int,
    val failures: List<String>,
    val restartedVpn: Boolean,
) {
    val shouldRetry: Boolean
        get() = failures.isNotEmpty()
}

/** Runs eligible updates, persists retry state, and isolates per-profile failures. */
internal class ProfileAutoUpdateRunner(
    private val operations: ProfileAutoUpdateOperations,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(): ProfileAutoUpdateResult {
        val allProfiles = operations.listProfiles()
        val profiles = ProfileAutoUpdatePolicy.eligibleProfiles(allProfiles, now())
        val failures = mutableListOf<String>()
        var updated = 0
        var activeProfileUpdated = false

        profiles.forEach { profile ->
            val attemptedAt = now()
            try {
                operations.updateRemoteProfile(profile.id)
                operations.recordAutoUpdateState(
                    profile.id,
                    ProfileAutoUpdatePolicy.successState(attemptedAt),
                )
                updated += 1
                activeProfileUpdated = activeProfileUpdated || profile.isActive
            } catch (error: Exception) {
                runCatching {
                    operations.recordAutoUpdateState(
                        profile.id,
                        ProfileAutoUpdatePolicy.failureState(
                            previousFailures = profile.autoUpdateFailures,
                            attemptedAt = attemptedAt,
                            error = error,
                        ),
                    )
                }
                failures += "${profile.id}:${error::class.java.simpleName}"
            }
        }

        var restartedVpn = false
        if (activeProfileUpdated && operations.serviceState.value == ServiceState.RUNNING) {
            runCatching { operations.restartVpn() }
                .onSuccess { restartedVpn = true }
                .onFailure { error ->
                    failures += "restart:${error::class.java.simpleName}"
                }
        }

        val configured = allProfiles.count { ProfileAutoUpdatePolicy.shouldSchedule(listOf(it)) }
        return ProfileAutoUpdateResult(
            attempted = profiles.size,
            updated = updated,
            deferred = configured - profiles.size,
            failures = failures,
            restartedVpn = restartedVpn,
        )
    }
}
