package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import rs.chimera.android.backend.model.ServiceState

internal sealed interface ProfileUpdateRuntimeApplyResult {
    data object NotRequired : ProfileUpdateRuntimeApplyResult

    data object Reloaded : ProfileUpdateRuntimeApplyResult

    data class Failed(val error: Exception) : ProfileUpdateRuntimeApplyResult
}

internal suspend fun applyUpdatedProfileToRunningVpn(
    updatedProfileId: String,
    activeProfileId: () -> String?,
    serviceState: () -> ServiceState,
    restartVpn: suspend () -> Unit,
): ProfileUpdateRuntimeApplyResult {
    if (activeProfileId() != updatedProfileId || serviceState() != ServiceState.RUNNING) {
        return ProfileUpdateRuntimeApplyResult.NotRequired
    }

    return try {
        restartVpn()
        ProfileUpdateRuntimeApplyResult.Reloaded
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        ProfileUpdateRuntimeApplyResult.Failed(error)
    }
}
