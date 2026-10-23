package rs.chimera.android.ui.metacubex.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.RemoteProfileRequest

internal sealed interface RemoteProfileImportState {
    data object Idle : RemoteProfileImportState
    data object Importing : RemoteProfileImportState
    data class Completed(val name: String) : RemoteProfileImportState
    data class Failed(val error: Exception) : RemoteProfileImportState
}

internal class RemoteProfileImportCoordinator(
    private val scope: CoroutineScope,
    private val importProfile: suspend (RemoteProfileRequest) -> String,
) {
    private val mutableState = MutableStateFlow<RemoteProfileImportState>(RemoteProfileImportState.Idle)
    val state: StateFlow<RemoteProfileImportState> = mutableState.asStateFlow()

    fun submit(request: RemoteProfileRequest): Boolean {
        if (mutableState.value != RemoteProfileImportState.Idle) return false

        mutableState.value = RemoteProfileImportState.Importing
        scope.launch {
            try {
                mutableState.value = RemoteProfileImportState.Completed(importProfile(request))
            } catch (error: CancellationException) {
                mutableState.value = RemoteProfileImportState.Idle
                throw error
            } catch (error: Exception) {
                mutableState.value = RemoteProfileImportState.Failed(error)
            }
        }
        return true
    }

    fun acknowledgeFailure() {
        if (mutableState.value is RemoteProfileImportState.Failed) {
            mutableState.value = RemoteProfileImportState.Idle
        }
    }
}

internal class MetaNewProfileViewModel(
    backend: ChimeraBackend = BackendProvider.provide(),
) : ViewModel() {
    private val importCoordinator = RemoteProfileImportCoordinator(viewModelScope) { request ->
        backend.importRemoteProfile(request)
    }

    val importState: StateFlow<RemoteProfileImportState> = importCoordinator.state

    fun submit(request: RemoteProfileRequest): Boolean = importCoordinator.submit(request)

    fun acknowledgeFailure() = importCoordinator.acknowledgeFailure()
}
