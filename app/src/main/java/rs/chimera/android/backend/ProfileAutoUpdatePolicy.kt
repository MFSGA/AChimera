package rs.chimera.android.backend

import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ServiceState

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
    private val retryRequired: Boolean,
) {
    val shouldRetry: Boolean
        get() = retryRequired
}

/** Runs eligible updates, persists retry state, and isolates per-profile failures. */
internal class ProfileAutoUpdateRunner(
    private val operations: ProfileAutoUpdateOperations,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(): ProfileAutoUpdateResult {
        val allProfiles = runCatching { operations.listProfiles() }
            .getOrElse { error ->
                error.throwIfCancellationOrFatal()
                return ProfileAutoUpdateResult(
                    attempted = 0,
                    updated = 0,
                    deferred = 0,
                    failures = listOf("list:${error::class.java.simpleName}"),
                    restartedVpn = false,
                    retryRequired = true,
                )
            }
        val profiles = ProfileAutoUpdatePolicy.eligibleProfiles(allProfiles, now())
        val failures = mutableListOf<String>()
        var updated = 0
        var activeProfileUpdated = false
        var retryRequired = false

        profiles.forEach { profile ->
            val attemptedAt = now()
            try {
                operations.updateRemoteProfile(profile.id)
                updated += 1
                activeProfileUpdated = activeProfileUpdated || profile.isActive
                runCatching {
                    operations.recordAutoUpdateState(
                        profile.id,
                        ProfileAutoUpdatePolicy.successState(attemptedAt),
                    )
                }.onFailure { error ->
                    error.throwIfCancellationOrFatal()
                    failures += "state:${profile.id}:${error::class.java.simpleName}"
                }
            } catch (error: Exception) {
                error.throwIfCancellationOrFatal()
                retryRequired = true
                val failureStillCurrent = runCatching {
                    operations.listProfiles()
                        .firstOrNull { it.id == profile.id }
                        ?.let { currentProfile ->
                            profile.hasSameAutoUpdateSource(currentProfile) &&
                                currentProfile.lastUpdated == profile.lastUpdated
                        } == true
                }.getOrElse { listError ->
                    listError.throwIfCancellationOrFatal()
                    failures += "list:${listError::class.java.simpleName}"
                    false
                }
                if (failureStillCurrent) {
                    runCatching {
                        operations.recordAutoUpdateState(
                            profile.id,
                            ProfileAutoUpdatePolicy.failureState(
                                previousFailures = profile.autoUpdateFailures,
                                attemptedAt = attemptedAt,
                                error = error,
                            ),
                        )
                    }.onFailure { stateError ->
                        stateError.throwIfCancellationOrFatal()
                        failures += "state:${profile.id}:${stateError::class.java.simpleName}"
                    }
                }
                failures += "${profile.id}:${error::class.java.simpleName}"
            }
        }

        var restartedVpn = false
        if (activeProfileUpdated && operations.serviceState.value == ServiceState.RUNNING) {
            runCatching { operations.restartVpn() }
                .onSuccess { restartedVpn = true }
                .onFailure { error ->
                    error.throwIfCancellationOrFatal()
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
            retryRequired = retryRequired,
        )
    }
}
