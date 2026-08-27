package rs.chimera.android.backend

internal data class ProfileMetadataScheduleActions(
    val requestImmediateAfterRefresh: Boolean,
    val requestImmediateAfterFailure: Boolean,
)

internal fun profileMetadataScheduleActions(
    autoUpdateEnabled: Boolean,
    invalidatesAutoUpdateState: Boolean,
): ProfileMetadataScheduleActions =
    ProfileMetadataScheduleActions(
        requestImmediateAfterRefresh = autoUpdateEnabled && invalidatesAutoUpdateState,
        requestImmediateAfterFailure = true,
    )
