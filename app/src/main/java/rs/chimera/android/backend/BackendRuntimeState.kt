package rs.chimera.android.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.chimera.android.backend.model.ServiceState

internal object BackendRuntimeState {
    private val mutableServiceState = MutableStateFlow(ServiceState.STOPPED)
    private val mutableServiceError = MutableStateFlow<String?>(null)

    val serviceState = mutableServiceState.asStateFlow()
    val serviceError = mutableServiceError.asStateFlow()

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
}
