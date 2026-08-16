package rs.chimera.android.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.VpnSystemStatus

internal object BackendRuntimeState {
    private val mutableServiceState = MutableStateFlow(ServiceState.STOPPED)
    private val mutableServiceError = MutableStateFlow<String?>(null)
    private val mutableVpnSystemStatus = MutableStateFlow(VpnSystemStatus())

    val serviceState = mutableServiceState.asStateFlow()
    val serviceError = mutableServiceError.asStateFlow()
    val vpnSystemStatus = mutableVpnSystemStatus.asStateFlow()

    fun updateServiceState(state: ServiceState) {
        mutableServiceState.value = state
        if (state != ServiceState.ERROR) {
            mutableServiceError.value = null
        }
    }

    fun updateServiceError(message: String) {
        mutableServiceError.value = message
        mutableServiceState.value = ServiceState.ERROR
    }

    fun updateVpnSystemStatus(status: VpnSystemStatus) {
        mutableVpnSystemStatus.value = status
    }
}
