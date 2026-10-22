package rs.chimera.android.ui.metacubex.design

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import rs.chimera.android.backend.model.ProviderSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.VpnRuntimeStatus

internal object ProviderDisplayPolicy {
    fun isVisible(provider: ProviderSnapshot): Boolean =
        !provider.vehicleType.equals("Compatible", ignoreCase = true)
}

internal fun Flow<VpnRuntimeStatus>.providerRuntimeStates(): Flow<ServiceState> =
    map { it.state }
        .distinctUntilChanged()
