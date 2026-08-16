package rs.chimera.android.ui.metacubex.activity

import rs.chimera.android.backend.model.ServiceState
import uniffi.chimera_ffi.Mode

internal object MetaMainModePolicy {
    fun visibleMode(
        serviceState: ServiceState,
        mode: Mode?,
    ): Mode? = mode.takeIf { serviceState == ServiceState.RUNNING }
}
