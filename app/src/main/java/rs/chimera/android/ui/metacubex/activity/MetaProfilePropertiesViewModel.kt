package rs.chimera.android.ui.metacubex.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.RemoteProfileSettings
import rs.chimera.android.ui.metacubex.design.MetaRemoteProfileForm

internal sealed interface ProfilePropertiesSaveState {
    data object Idle : ProfilePropertiesSaveState
    data object Saving : ProfilePropertiesSaveState
    data object Saved : ProfilePropertiesSaveState
    data class Failed(val error: Exception) : ProfilePropertiesSaveState
}

internal class ProfilePropertiesSaveCoordinator(private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow<ProfilePropertiesSaveState>(ProfilePropertiesSaveState.Idle)
    val state = mutableState.asStateFlow()

    fun submit(save: suspend () -> Unit): Boolean {
        if (mutableState.value != ProfilePropertiesSaveState.Idle) return false
        mutableState.value = ProfilePropertiesSaveState.Saving
        scope.launch {
            try {
                save()
                mutableState.value = ProfilePropertiesSaveState.Saved
            } catch (cancellation: CancellationException) {
                mutableState.value = ProfilePropertiesSaveState.Idle
                throw cancellation
            } catch (error: Exception) {
                mutableState.value = ProfilePropertiesSaveState.Failed(error)
            }
        }
        return true
    }

    fun acknowledgeFailure() {
        if (mutableState.value is ProfilePropertiesSaveState.Failed) mutableState.value = ProfilePropertiesSaveState.Idle
    }
}

internal class MetaProfilePropertiesViewModel(
    private val backend: ChimeraBackend = BackendProvider.provide(),
) : ViewModel() {
    private val coordinator = ProfilePropertiesSaveCoordinator(viewModelScope)
    val saveState = coordinator.state

    suspend fun loadProfile(id: String): ProfileSummary? {
        backend.awaitReady()
        return backend.listProfiles().firstOrNull { it.id == id }
    }

    fun save(profile: ProfileSummary, form: MetaRemoteProfileForm): Boolean = coordinator.submit {
        val name = requireNotNull(form.name).trim()
        require(name.isNotEmpty())
        if (profile.isRemote) {
            backend.updateRemoteProfileSettings(
                profile.id,
                RemoteProfileSettings(name, form.url, form.autoUpdate, form.userAgent, form.proxyUrl, form.autoUpdateIntervalMillis),
                expectedIntervalMillis = form.expectedAutoUpdateIntervalMillis ?: profile.autoUpdateIntervalMillis,
            )
        } else {
            backend.renameProfile(profile.id, name)
        }
    }

    fun acknowledgeFailure() = coordinator.acknowledgeFailure()
}
