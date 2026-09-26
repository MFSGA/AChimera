package rs.chimera.android.backend

import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.ProfileSummary

internal sealed interface ProfileRemoteBatchUpdateResult {
    data object NoRemoteProfiles : ProfileRemoteBatchUpdateResult

    data class Completed(
        val batch: ProfileBatchUpdateResult,
    ) : ProfileRemoteBatchUpdateResult
}

internal fun ChimeraBackend.profileRemoteBatchUpdateRunner() = ProfileRemoteBatchUpdateRunner(
    listProfiles = ::listProfiles,
    updateProfile = ::updateRemoteProfile,
    activeProfileId = { activeProfile.value?.id },
    serviceState = { serviceState.value },
    restartVpn = ::restartVpn,
)

internal class ProfileRemoteBatchUpdateRunner(
    private val listProfiles: suspend () -> List<ProfileSummary>,
    private val updateProfile: suspend (String) -> Unit,
    private val activeProfileId: () -> String?,
    private val serviceState: () -> ServiceState,
    private val restartVpn: suspend () -> Unit,
) {
    suspend fun run(): ProfileRemoteBatchUpdateResult {
        val remoteProfileIds = listProfiles().filter { it.isRemote }.map { it.id }
        if (remoteProfileIds.isEmpty()) return ProfileRemoteBatchUpdateResult.NoRemoteProfiles

        return ProfileRemoteBatchUpdateResult.Completed(
            updateRemoteProfilesBatch(
                profileIds = remoteProfileIds,
                updateProfile = updateProfile,
                activeProfileId = activeProfileId,
                serviceState = serviceState,
                restartVpn = restartVpn,
            ),
        )
    }
}
