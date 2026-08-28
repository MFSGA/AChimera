package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.service.VpnStopInProgressException

internal data class ProfileActivationRollbackState(
    val catalog: String,
    val activePath: String?,
)

internal suspend fun <T> runProfileActivationTransaction(block: suspend () -> T): T =
    withContext(NonCancellable) { block() }

internal fun applyProfileActivationSelection(
    activePath: String?,
    updateRuntimePath: (String?) -> Unit,
    refreshActiveProfile: () -> Unit,
) {
    updateRuntimePath(activePath)
    refreshActiveProfile()
}

internal fun commitProfileActivationSelection(
    persistActivation: () -> Unit,
    restoreSelection: () -> Unit,
    rollbackActivation: () -> Unit,
) {
    persistActivation()
    try {
        restoreSelection()
    } catch (error: Throwable) {
        try {
            rollbackActivation()
        } catch (rollbackError: Exception) {
            error.addSuppressed(rollbackError)
        }
        throw error
    }
}

internal suspend fun applyProfileActivationToRunningVpn(
    serviceState: StateFlow<ServiceState>,
    restartVpn: suspend () -> Unit,
    rollbackActivation: suspend () -> Unit,
) {
    val settledState = if (serviceState.value == ServiceState.STARTING) {
        serviceState.first { it != ServiceState.STARTING }
    } else {
        serviceState.value
    }
    if (settledState != ServiceState.RUNNING) return

    try {
        restartVpn()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (
            error is VpnStopInProgressException ||
            serviceState.value == ServiceState.STOPPING ||
            serviceState.value == ServiceState.STOPPED ||
            serviceState.value == ServiceState.ERROR
        ) {
            return
        }
        try {
            rollbackActivation()
        } catch (rollbackError: Exception) {
            error.addSuppressed(rollbackError)
        }
        throw error
    }
}
