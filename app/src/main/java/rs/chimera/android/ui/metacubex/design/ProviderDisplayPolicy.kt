package rs.chimera.android.ui.metacubex.design

import rs.chimera.android.backend.model.ProviderSnapshot

internal object ProviderDisplayPolicy {
    fun isVisible(provider: ProviderSnapshot): Boolean =
        !provider.vehicleType.equals("Compatible", ignoreCase = true)
}
