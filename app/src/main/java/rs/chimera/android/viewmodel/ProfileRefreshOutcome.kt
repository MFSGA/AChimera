package rs.chimera.android.viewmodel

internal enum class ProfileRefreshOutcome {
    APPLIED,
    STALE,
    FAILED,
    ;

    val shouldPublishOperationStatus: Boolean
        get() = this == APPLIED
}
