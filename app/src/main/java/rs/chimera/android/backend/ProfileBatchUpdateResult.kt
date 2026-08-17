package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import rs.chimera.android.backend.model.ServiceState

internal data class ProfileBatchUpdateResult(
    val succeeded: Int,
    val failed: Int,
    val runtimeApply: ProfileUpdateRuntimeApplyResult,
)

internal suspend fun updateRemoteProfilesBatch(
    profileIds: List<String>,
    updateProfile: suspend (String) -> Unit,
    activeProfileId: () -> String?,
    serviceState: () -> ServiceState,
    restartVpn: suspend () -> Unit,
): ProfileBatchUpdateResult {
    val updatedProfileIds = mutableSetOf<String>()
    var failed = 0

    profileIds.distinct().forEach { profileId ->
        try {
            updateProfile(profileId)
            updatedProfileIds += profileId
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed++
        }
    }

    val activeUpdatedId = activeProfileId()?.takeIf(updatedProfileIds::contains)
    val runtimeApply = if (activeUpdatedId != null) {
        applyUpdatedProfileToRunningVpn(
            updatedProfileId = activeUpdatedId,
            activeProfileId = activeProfileId,
            serviceState = serviceState,
            restartVpn = restartVpn,
        )
    } else {
        ProfileUpdateRuntimeApplyResult.NotRequired
    }

    return ProfileBatchUpdateResult(
        succeeded = updatedProfileIds.size,
        failed = failed,
        runtimeApply = runtimeApply,
    )
}
