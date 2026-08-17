package rs.chimera.android.backend

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.ffi.shutdownClash
import rs.chimera.android.service.TunService
import rs.chimera.android.service.VpnDesiredStateReason
import rs.chimera.android.service.VpnDesiredStateStore
import rs.chimera.android.service.VpnRuntimeRegistry
import rs.chimera.android.util.toUserVisibleMessage

internal class BackendVpnOperations(
    private val context: Context,
    private val serviceState: StateFlow<ServiceState>,
    private val profilePath: () -> String,
) {
    private val commands = VpnCommandGate { serviceState.value }
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

    suspend fun startVpnAfterPermission() = commands.start {
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
            BackendRuntimeState.updateServiceError(error.toServiceError())
            throw error
        }
    }

    suspend fun stopVpn() = withContext(Dispatchers.IO) {
        // Cancel a pending Android start immediately; persist again inside the command gate
        // so a start already holding the gate cannot overwrite the user's stop intent.
        VpnRuntimeRegistry.requestStop()
        commands.stop { shouldStopRuntime ->
            val desiredStateError =
                runCatching { desiredStateStore.markStopped(VpnDesiredStateReason.USER_STOP) }
                    .exceptionOrNull()
            if (!shouldStopRuntime) {
                if (desiredStateError != null && desiredStateStore.snapshot().shouldRun) {
                    BackendRuntimeState.updateServiceError(desiredStateError.toServiceError())
                    throw desiredStateError
                }
                return@stop
            }
            BackendRuntimeState.updateServiceState(ServiceState.STOPPING)
            try {
                if (!VpnRuntimeRegistry.stopVpn()) {
                    shutdownClash().getOrThrow()
                    BackendRuntimeState.updateServiceState(ServiceState.STOPPED)
                }
            } catch (error: Exception) {
                desiredStateError?.let(error::addSuppressed)
                BackendRuntimeState.updateServiceError(error.toServiceError())
                BackendRuntimeState.updateServiceState(ServiceState.ERROR)
                throw error
            }
            if (desiredStateError != null && desiredStateStore.snapshot().shouldRun) {
                BackendRuntimeState.updateServiceError(desiredStateError.toServiceError())
                throw desiredStateError
            }
        }
    }

    suspend fun restartVpn() = withContext(Dispatchers.IO) {
        commands.restart {
            VpnRuntimeRegistry.restartVpn()
        }
    }

    private fun Throwable.toServiceError(): String =
        toUserVisibleMessage(context, rs.chimera.android.R.string.profile_unknown_error)
}
