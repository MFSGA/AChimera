package rs.chimera.android.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.ServiceState

/** Keeps UI navigation from destroying an activity while a VPN action is pending. */
object VpnUiActionGate {
    class Lease internal constructor(val owner: String)

    private val mutableVpnActionInProgress = MutableStateFlow(false)
    private val leases = VpnUiActionLeaseStore()

    val vpnActionInProgress: StateFlow<Boolean> = mutableVpnActionInProgress

    @Synchronized
    fun tryBeginVpnAction(owner: String): Lease? {
        return leases.tryBegin(owner, isVpnTransitioning())?.also {
            mutableVpnActionInProgress.value = true
        }
    }

    @Synchronized
    fun resumeVpnAction(owner: String): Lease? {
        return leases.resume(owner)?.also {
            mutableVpnActionInProgress.value = true
        }
    }

    @Synchronized
    fun finishVpnAction(lease: Lease) {
        if (leases.finish(lease)) mutableVpnActionInProgress.value = false
    }

    @Synchronized
    fun canSwitchUi(): Boolean =
        !leases.isActive && !isVpnTransitioning()

    @Synchronized
    fun runIfUiSwitchAllowed(action: () -> Unit): Boolean {
        if (leases.isActive || isVpnTransitioning()) return false
        action()
        return true
    }

    private fun isVpnTransitioning(): Boolean =
        BackendProvider.provide().serviceState.value in setOf(
            ServiceState.STARTING,
            ServiceState.STOPPING,
        )
}

internal class VpnUiActionLeaseStore {
    private var activeLease: VpnUiActionGate.Lease? = null

    val isActive: Boolean
        get() = activeLease != null

    fun tryBegin(owner: String, vpnTransitioning: Boolean): VpnUiActionGate.Lease? {
        activeLease?.takeIf { it.owner == owner }?.let { return it }
        if (activeLease != null || vpnTransitioning) return null
        return VpnUiActionGate.Lease(owner).also { activeLease = it }
    }

    fun resume(owner: String): VpnUiActionGate.Lease? {
        if (activeLease != null && activeLease?.owner != owner) return null
        return VpnUiActionGate.Lease(owner).also { activeLease = it }
    }

    fun finish(lease: VpnUiActionGate.Lease): Boolean {
        if (activeLease !== lease) return false
        activeLease = null
        return true
    }
}
