package rs.chimera.android.backend

import kotlinx.coroutines.flow.StateFlow
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
    updateProfile = { id, afterCommit -> updateRemoteProfile(id) { _ -> afterCommit() } },
    activeProfileId = { activeProfile.value?.id },
    serviceState = serviceState,
    restartVpn = ::restartVpn,
)

internal class ProfileRemoteBatchUpdateRunner(
    private val listProfiles: suspend () -> List<ProfileSummary>,
    private val updateProfile: suspend (String, suspend () -> Unit) -> Unit,
    private val activeProfileId: () -> String?,
    private val serviceState: StateFlow<ServiceState>,
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
