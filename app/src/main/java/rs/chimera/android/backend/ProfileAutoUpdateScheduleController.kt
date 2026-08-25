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
        afterRefresh: () -> Unit = {},
    ): ProfileAutoUpdateScheduleSyncResult =
        ProfileAutoUpdateScheduleSync.run(
            loadProfiles = loadProfiles,
            refreshSchedule = scheduler::refresh,
            afterRefresh = afterRefresh,
            onFailure = onFailure,
        )

    fun requestImmediateRefresh() {
        scheduler.requestImmediateRefresh()
    }
}
