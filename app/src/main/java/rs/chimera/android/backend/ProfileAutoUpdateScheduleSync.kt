package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.util.runCatchingPreservingCancellation

internal object ProfileAutoUpdateScheduleSync {
    suspend fun run(
        loadProfiles: suspend () -> List<ProfileSummary>,
        refreshSchedule: (List<ProfileSummary>) -> Unit,
        onFailure: (Throwable) -> Unit,
    ) {
        runCatchingPreservingCancellation { loadProfiles() }
            .onSuccess(refreshSchedule)
            .onFailure(onFailure)
    }
}
