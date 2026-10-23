package rs.chimera.android.service

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.chimera.android.backend.model.ServiceState

internal class VpnStopInProgressException : IllegalStateException("VPN service is stopping")

internal interface VpnRuntimeControl {
    fun protectSocket(fd: Int): Boolean

    fun onCoreStopped(message: String)

    suspend fun stopVpn()

    suspend fun restartVpn()
}

internal object VpnRuntimeRegistry {
    private val lock = Any()

    @Volatile
    private var control: VpnRuntimeControl? = null
    private val runRequested = AtomicBoolean(false)
    private val mutableRunRequested = MutableStateFlow(false)
    private val settingsMutationGate = VpnSettingsMutationGate(lock) {
        runRequested.get() || VpnDesiredStateRuntimeState.shouldRun.value
    }

    val runRequestedState = mutableRunRequested.asStateFlow()

    val isRegistered: Boolean
        get() = control != null

    val shouldRun: Boolean
        get() = runRequested.get()

    fun <T> withSettingsMutation(
        serviceState: ServiceState,
        unavailableMessage: () -> String,
        action: () -> T,
    ): T = settingsMutationGate.withSettingsMutation(serviceState, unavailableMessage, action)

    fun requestStart(onRequest: () -> Unit = {}) = synchronized(lock) {
        onRequest()
        runRequested.set(true)
        mutableRunRequested.value = true
    }

    fun requestStop() = synchronized(lock) {
        runRequested.set(false)
        mutableRunRequested.value = false
    }

    fun register(runtime: VpnRuntimeControl): Boolean = synchronized(lock) {
        if (!runRequested.get()) return@synchronized false
        control = runtime
        true
    }

    fun unregister(runtime: VpnRuntimeControl) {
        synchronized(lock) {
            if (control === runtime) {
                control = null
            }
        }
    }

    fun protectSocket(fd: Int): Boolean = control?.protectSocket(fd) == true

    fun dispatchCoreStopped(message: String): Boolean {
        val runtime = control ?: return false
        runtime.onCoreStopped(message)
        return true
    }

    suspend fun stopVpn(): Boolean {
        requestStop()
        val runtime = control ?: return false
        runtime.stopVpn()
        return true
    }

    suspend fun restartVpn() {
        val runtime = checkNotNull(control) { "VPN service is unavailable" }
        runtime.restartVpn()
    }
}
