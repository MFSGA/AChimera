package rs.chimera.android.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.chimera.android.backend.model.ServiceState

internal object BackendRuntimeState {
    private val mutableServiceState = MutableStateFlow(ServiceState.STOPPED)

    val serviceState = mutableServiceState.asStateFlow()

    fun updateServiceState(state: ServiceState) {
        mutableServiceState.value = state
    }
}
