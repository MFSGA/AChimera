package rs.chimera.android.ui

import android.content.Context
import rs.chimera.android.R
import rs.chimera.android.backend.model.VpnSystemStatus

internal enum class VpnSystemStatusDisplayMode {
    UNOBSERVED,
    CURRENT,
    LAST_OBSERVED,
}

internal data class VpnSystemStatusPresentation(
    val mode: VpnSystemStatusDisplayMode,
    val alwaysOn: Boolean,
    val lockdown: Boolean,
) {
    val restricted: Boolean
        get() = lockdown
}

internal fun VpnSystemStatus.resolvePresentation(): VpnSystemStatusPresentation =
    VpnSystemStatusPresentation(
        mode = when {
            !observed -> VpnSystemStatusDisplayMode.UNOBSERVED
            serviceActive -> VpnSystemStatusDisplayMode.CURRENT
            else -> VpnSystemStatusDisplayMode.LAST_OBSERVED
        },
        alwaysOn = alwaysOn,
        lockdown = lockdown,
    )

internal fun VpnSystemStatusPresentation.format(context: Context): String {
    if (mode == VpnSystemStatusDisplayMode.UNOBSERVED) {
        return context.getString(R.string.vpn_system_status_unobserved)
    }
    val alwaysOnLabel = context.getString(
        if (alwaysOn) R.string.status_enabled else R.string.status_disabled,
    )
    val lockdownLabel = context.getString(
        if (lockdown) R.string.status_enabled else R.string.status_disabled,
    )
    val summary = context.getString(
        if (mode == VpnSystemStatusDisplayMode.CURRENT) {
            R.string.vpn_system_status_current
        } else {
            R.string.vpn_system_status_last_observed
        },
        alwaysOnLabel,
        lockdownLabel,
    )
    return if (lockdown) {
        context.getString(R.string.vpn_system_status_lockdown_warning, summary)
    } else {
        summary
    }
}
