package rs.chimera.android.backend

import android.content.Context
import rs.chimera.android.backend.model.ProfileSummary

internal class ProfileAutoUpdateScheduleController(
    context: Context,
    private val loadProfiles: suspend () -> List<ProfileSummary>,
    private val onFailure: (Throwable) -> Unit,
) {
    private val scheduler = ProfileAutoUpdateScheduler(context)

    suspend fun synchronize(
        afterRefresh: (List<ProfileSummary>) -> Unit = {},
        afterFailure: () -> Unit = {},
    ): ProfileAutoUpdateScheduleSyncResult =
        ProfileAutoUpdateScheduleSync.run(
            loadProfiles = loadProfiles,
            refreshSchedule = scheduler::refresh,
            afterRefresh = afterRefresh,
            afterFailure = afterFailure,
            onFailure = onFailure,
        )

    fun requestImmediateRefresh() {
        scheduler.requestImmediateRefresh()
    }
}
