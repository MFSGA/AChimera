package rs.chimera.android.ui.metacubex.activity

import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.ProxyMode

internal object MetaMainModePolicy {
    fun visibleMode(
        serviceState: ServiceState,
        mode: ProxyMode?,
    ): ProxyMode? = mode.takeIf { serviceState == ServiceState.RUNNING }
}
