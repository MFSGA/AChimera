package rs.chimera.android.backend

import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ServiceState

/** Selects the profiles that are configured for a scheduled remote refresh. */
internal object ProfileAutoUpdatePolicy {
    fun eligibleProfiles(profiles: List<ProfileSummary>): List<ProfileSummary> =
        profiles.filter { profile ->
            profile.type == ProfileType.REMOTE &&
                profile.isRemote &&
                profile.autoUpdate &&
                !profile.url.isNullOrBlank()
        }

    fun shouldSchedule(profiles: List<ProfileSummary>): Boolean =
        eligibleProfiles(profiles).isNotEmpty()
}

internal interface ProfileAutoUpdateOperations {
    val serviceState: StateFlow<ServiceState>

    suspend fun listProfiles(): List<ProfileSummary>

    suspend fun updateRemoteProfile(id: String)

    suspend fun restartVpn()
}

internal data class ProfileAutoUpdateResult(
    val attempted: Int,
    val updated: Int,
    val failures: List<String>,
    val restartedVpn: Boolean,
) {
    val shouldRetry: Boolean
        get() = failures.isNotEmpty()
}

/** Runs all eligible updates and isolates failures so one bad URL does not block others. */
internal class ProfileAutoUpdateRunner(
    private val operations: ProfileAutoUpdateOperations,
) {
    suspend fun run(): ProfileAutoUpdateResult {
        val profiles = ProfileAutoUpdatePolicy.eligibleProfiles(operations.listProfiles())
        val failures = mutableListOf<String>()
        var updated = 0
        var activeProfileUpdated = false

        profiles.forEach { profile ->
            runCatching { operations.updateRemoteProfile(profile.id) }
                .onSuccess {
                    updated += 1
                    activeProfileUpdated = activeProfileUpdated || profile.isActive
                }
                .onFailure { error ->
                    failures += "${profile.id}:${error.message ?: error::class.java.simpleName}"
                }
        }

        var restartedVpn = false
        if (activeProfileUpdated && operations.serviceState.value == ServiceState.RUNNING) {
            runCatching { operations.restartVpn() }
                .onSuccess { restartedVpn = true }
                .onFailure { error ->
                    failures += "restart:${error.message ?: error::class.java.simpleName}"
                }
        }

        return ProfileAutoUpdateResult(
            attempted = profiles.size,
            updated = updated,
            failures = failures,
            restartedVpn = restartedVpn,
        )
    }
}
