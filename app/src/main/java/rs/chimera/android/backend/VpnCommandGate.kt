package rs.chimera.android.backend

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rs.chimera.android.backend.model.ServiceState

/** Both UI variants enter the same command gate; Android callbacks retain their lifecycle gate. */
internal class VpnCommandGate(private val state: () -> ServiceState) {
    private val mutex = Mutex()

    suspend fun start(action: suspend () -> Unit) = mutex.withLock {
        when (state()) {
            ServiceState.RUNNING, ServiceState.STARTING -> Unit
            ServiceState.STOPPING -> error("VPN service is stopping")
            ServiceState.STOPPED, ServiceState.ERROR -> action()
        }
    }

    suspend fun stop(action: suspend (shouldStopRuntime: Boolean) -> Unit) = mutex.withLock {
        action(state() != ServiceState.STOPPED && state() != ServiceState.STOPPING)
    }

    suspend fun restart(action: suspend () -> Unit) {
        check(mutex.tryLock()) { "Another VPN operation is already in progress" }
        try {
            check(state() == ServiceState.RUNNING) { "VPN is not running" }
            action()
        } finally {
            mutex.unlock()
        }
    }
}
