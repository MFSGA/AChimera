package rs.chimera.android.service

internal enum class VpnRecoveryDecision {
    RESTORE,
    DISCARD_STOPPED,
    BLOCK_MISSING_PROFILE,
    BLOCK_PERMISSION_REQUIRED,
}

internal object VpnPermissionPolicy {
    fun isRequired(routeSystemTraffic: Boolean): Boolean = routeSystemTraffic
}

internal object VpnRecoveryPolicy {
    fun decide(
        snapshot: VpnDesiredStateSnapshot,
        profileAvailable: Boolean,
        permissionGranted: Boolean,
        systemAlwaysOnEnabled: Boolean = false,
    ): VpnRecoveryDecision = when {
        !snapshot.shouldRun && !systemAlwaysOnEnabled -> VpnRecoveryDecision.DISCARD_STOPPED
        !profileAvailable -> VpnRecoveryDecision.BLOCK_MISSING_PROFILE
        !permissionGranted -> VpnRecoveryDecision.BLOCK_PERMISSION_REQUIRED
        else -> VpnRecoveryDecision.RESTORE
    }
}
