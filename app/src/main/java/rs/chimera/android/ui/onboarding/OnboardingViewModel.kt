package rs.chimera.android.ui.onboarding

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ProfileRemotePolicy
import rs.chimera.android.backend.model.ProfileDownloadProgress
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.UiVariant

internal class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val backend = BackendProvider.provide()
    private val appContext = application.applicationContext
    private var initialized = false
    private var retryAction: (() -> Unit)? = null

    var step by mutableStateOf(OnboardingStep.WELCOME)
        private set

    var selectedUi by mutableStateOf(UiVariant.WATFAQ)
        private set

    var isWorking by mutableStateOf(false)
        private set

    var isCheckingProfiles by mutableStateOf(false)
        private set

    var profileLookupFailed by mutableStateOf(false)
        private set

    var importFailure by mutableStateOf<ProfileImportFailure?>(null)
        private set

    var downloadProgress by mutableStateOf<ProfileDownloadProgress?>(null)
        private set

    var importedProfileName by mutableStateOf<String?>(null)
        private set

    var connectionFailure by mutableStateOf(false)
        private set

    fun initialize(firstRun: Boolean) {
        if (initialized) return
        initialized = true
        selectedUi = if (firstRun) {
            FirstRunOnboardingStore.selectedUi(appContext) ?: OnboardingPolicy.defaultRecommendedUi()
        } else {
            AppPreferences.uiVariant(appContext)
        }
        step = if (firstRun) FirstRunOnboardingStore.step(appContext) else OnboardingStep.WELCOME
        if (firstRun) FirstRunOnboardingStore.saveStep(appContext, step)
        if (firstRun && step == OnboardingStep.CONNECT) {
            viewModelScope.launch {
                try {
                    val profiles = backend.listProfiles()
                    if (profiles.isEmpty()) moveTo(OnboardingStep.PROFILE)
                    else {
                        importedProfileName = profiles.firstOrNull { it.isActive }?.name
                            ?: profiles.firstOrNull()?.name
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    profileLookupFailed = true
                }
            }
        }
    }

    fun chooseUi(variant: UiVariant) {
        selectedUi = variant
        FirstRunOnboardingStore.saveSelectedUi(appContext, variant)
    }

    fun goToUiSelection() {
        moveTo(OnboardingStep.UI_SELECTION)
    }

    fun confirmUi() {
        if (isCheckingProfiles) return
        AppPreferences.updateUiVariant(appContext, selectedUi)
        isCheckingProfiles = true
        profileLookupFailed = false
        viewModelScope.launch {
            try {
                val profiles = backend.listProfiles()
                importedProfileName = profiles.firstOrNull { it.isActive }?.name
                    ?: profiles.firstOrNull()?.name
                moveTo(OnboardingPolicy.stepAfterUiSelection(profiles.isNotEmpty()))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                profileLookupFailed = true
            } finally {
                isCheckingProfiles = false
            }
        }
    }

    fun retryProfileLookup() = confirmUi()

    fun importLocalProfile(uri: Uri) {
        retryAction = { importLocalProfile(uri) }
        importProfile(isRemoteImport = false) {
            backend.importLocalProfile(uri, null)
        }
    }

    fun importRemoteProfile(url: String) {
        val normalizedUrl = url.trim()
        if (!ProfileRemotePolicy.isValidUrl(normalizedUrl)) {
            importFailure = ProfileImportFailure(ProfileImportFailureType.INVALID_URL)
            return
        }
        retryAction = { importRemoteProfile(normalizedUrl) }
        importProfile(isRemoteImport = true) {
            backend.importRemoteProfile(RemoteProfileRequest(name = null, url = normalizedUrl)) {
                val progress = it
                viewModelScope.launch(Dispatchers.Main.immediate) { downloadProgress = progress }
            }
        }
    }

    fun showImportFailure(type: ProfileImportFailureType) {
        importFailure = ProfileImportFailure(type)
    }

    fun retryImport() {
        retryAction?.invoke()
    }

    fun clearImportFailure() {
        importFailure = null
    }

    fun markConnected() {
        connectionFailure = false
        moveTo(OnboardingStep.COMPLETE)
    }

    fun reportConnectionFailure() {
        connectionFailure = true
    }

    fun returnToConnectAfterDisconnect() {
        connectionFailure = true
        moveTo(OnboardingStep.CONNECT)
    }

    fun clearConnectionFailure() {
        connectionFailure = false
    }

    fun skipGuide() {
        FirstRunOnboardingStore.markCompleted(appContext)
    }

    fun finishGuide() {
        FirstRunOnboardingStore.markCompleted(appContext)
    }

    fun goBack() {
        when (step) {
            OnboardingStep.WELCOME -> Unit
            OnboardingStep.UI_SELECTION -> moveTo(OnboardingStep.WELCOME)
            OnboardingStep.PROFILE -> moveTo(OnboardingStep.UI_SELECTION)
            OnboardingStep.CONNECT -> moveTo(OnboardingStep.PROFILE)
            OnboardingStep.COMPLETE -> Unit
        }
    }

    private fun importProfile(isRemoteImport: Boolean, importer: suspend () -> String) {
        if (isWorking) return
        importFailure = null
        isWorking = true
        downloadProgress = null
        viewModelScope.launch {
            try {
                importedProfileName = importer()
                retryAction = null
                if (step == OnboardingStep.PROFILE) moveTo(OnboardingStep.CONNECT)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                importFailure = ProfileImportFailurePolicy.classify(error.message, isRemoteImport)
            } finally {
                isWorking = false
            }
        }
    }

    private fun moveTo(next: OnboardingStep) {
        step = next
        FirstRunOnboardingStore.saveStep(appContext, next)
    }
}
