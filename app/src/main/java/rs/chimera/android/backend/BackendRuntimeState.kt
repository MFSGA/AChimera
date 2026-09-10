package rs.chimera.android.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import rs.chimera.android.backend.model.VpnRuntimeStatus
import kotlinx.coroutines.flow.asStateFlow
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.VpnSystemStatus

internal object BackendRuntimeState {
    private val mutableStatus = MutableStateFlow(VpnRuntimeStatus())
    private val mutableVpnSystemStatus = MutableStateFlow(VpnSystemStatus())

    val status = mutableStatus.asStateFlow()
    val serviceState = StateFlowView(status) { it.state }
    val serviceError = StateFlowView(status) { it.error }
    val vpnSystemStatus = mutableVpnSystemStatus.asStateFlow()

    fun updateServiceState(state: ServiceState) {
        mutableStatus.update { previous ->
            VpnRuntimeStatus(state, previous.error.takeIf { state == ServiceState.ERROR })
        }
    }

    fun updateServiceError(message: String) {
        mutableStatus.value = VpnRuntimeStatus(ServiceState.ERROR, message)
    }

    fun updateVpnSystemStatus(status: VpnSystemStatus) {
        mutableVpnSystemStatus.value = status
    }
}
