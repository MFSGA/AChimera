package rs.chimera.android.backend

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.ffi.shutdownClash
import rs.chimera.android.service.TunService
import rs.chimera.android.service.VpnDesiredStateReason
import rs.chimera.android.service.VpnDesiredStateStore
import rs.chimera.android.service.VpnRuntimeRegistry

internal class BackendVpnOperations(
    private val context: Context,
    private val serviceState: StateFlow<ServiceState>,
    private val profilePath: () -> String,
) {
    private val operationMutex = Mutex()
    private val desiredStateStore = VpnDesiredStateStore(context)

    fun prepareStartVpn(): StartVpnResult {
        if (profilePath().isBlank()) {
            return StartVpnResult.Error(
                context.getString(rs.chimera.android.R.string.service_profile_required),
            )
        }

        val intent = VpnService.prepare(context)
        return if (intent != null) {
            StartVpnResult.Prepared(intent)
        } else {
            StartVpnResult.PermissionNotRequired
        }
    }

    fun startVpnAfterPermission() {
        desiredStateStore.markRunning()
        VpnRuntimeRegistry.requestStart()
        BackendRuntimeState.updateServiceState(ServiceState.STARTING)
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TunService::class.java),
            )
        } catch (error: Exception) {
            runCatching { desiredStateStore.markStopped(VpnDesiredStateReason.START_FAILED) }
                .onFailure(error::addSuppressed)
            VpnRuntimeRegistry.requestStop()
            BackendRuntimeState.updateServiceError(error.messageOrType())
            throw error
        }
    }

    suspend fun stopVpn() {
        val desiredStateError =
            runCatching { desiredStateStore.markStopped(VpnDesiredStateReason.USER_STOP) }
                .exceptionOrNull()
        VpnRuntimeRegistry.requestStop()
        operationMutex.withLock {
            BackendRuntimeState.updateServiceState(ServiceState.STOPPING)
            try {
                if (!VpnRuntimeRegistry.stopVpn()) {
                    shutdownClash().getOrThrow()
                    BackendRuntimeState.updateServiceState(ServiceState.STOPPED)
                }
            } catch (error: Exception) {
                desiredStateError?.let(error::addSuppressed)
                BackendRuntimeState.updateServiceError(error.messageOrType())
                throw error
            }
        }
        if (desiredStateError != null && desiredStateStore.snapshot().shouldRun) {
            BackendRuntimeState.updateServiceError(desiredStateError.messageOrType())
            throw desiredStateError
        }
    }

    suspend fun restartVpn() {
        check(operationMutex.tryLock()) { "Another VPN operation is already in progress" }
        try {
            check(serviceState.value == ServiceState.RUNNING) { "VPN is not running" }
            VpnRuntimeRegistry.restartVpn()
        } finally {
            operationMutex.unlock()
        }
    }

    private fun Throwable.messageOrType(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}
