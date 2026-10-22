package rs.chimera.android.ui.metacubex.design

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import rs.chimera.android.backend.model.ProviderSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.VpnRuntimeStatus

internal object ProviderDisplayPolicy {
    fun sorted(providers: List<ProviderSnapshot>): List<ProviderSnapshot> =
        providers.sortedWith(
            compareBy<ProviderSnapshot> { providerTypeOrder(it.type) }
                .thenBy { it.name },
        )

    private fun providerTypeOrder(type: String): Int = when (type.lowercase()) {
        "proxy" -> 0
        "rule" -> 1
        else -> 2
    }
}

internal fun Flow<VpnRuntimeStatus>.providerRuntimeStates(): Flow<ServiceState> =
    map { it.state }
        .distinctUntilChanged()
