package rs.chimera.android.viewmodel

internal enum class ProfileOperationKind {
    MUTATION,
    IMPORTING,
    DOWNLOADING,
    REFRESHING,
    VERIFYING,
}

internal data class ProfileOperationState(
    val kind: ProfileOperationKind? = null,
) {
    val isInProgress: Boolean
        get() = kind != null

    val isImporting: Boolean
        get() = kind == ProfileOperationKind.IMPORTING

    val isDownloading: Boolean
        get() = kind == ProfileOperationKind.DOWNLOADING

    val isRefreshingRemoteProfiles: Boolean
        get() = kind == ProfileOperationKind.REFRESHING

    companion object {
        fun active(kind: ProfileOperationKind): ProfileOperationState = ProfileOperationState(kind)
    }
}
