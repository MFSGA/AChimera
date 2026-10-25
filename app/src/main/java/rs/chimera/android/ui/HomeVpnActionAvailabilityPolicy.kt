package rs.chimera.android.ui

internal object HomeVpnActionAvailabilityPolicy {
    fun isEnabled(
        isVpnTransitioning: Boolean,
        isVpnActionInProgress: Boolean,
        isUiSwitchAllowed: Boolean = true,
    ): Boolean =
        !isVpnTransitioning &&
            !isVpnActionInProgress &&
            isUiSwitchAllowed
}
