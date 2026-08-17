package rs.chimera.android.backend

import kotlinx.coroutines.sync.Mutex

import rs.chimera.android.backend.model.ProxyMode

import rs.chimera.android.settings.SettingsRepository

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rs.chimera.android.Global
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ConnectionsSnapshot
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyProviderSnapshot
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.backend.model.RuleSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.SettingsApplyEffect
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.backend.model.VpnSystemStatus
import rs.chimera.android.ffi.ChimeraFfi
import rs.chimera.android.util.PrivacySafeLog
import rs.chimera.android.backend.model.ProfileDownloadProgress
import rs.chimera.android.util.toUserVisibleMessage
import uniffi.chimera_ffi.DownloadProgressCallback
import uniffi.chimera_ffi.downloadFileWithProgress
import uniffi.chimera_ffi.verifyConfig
import java.io.File

class ChimeraBackendImpl(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) : ChimeraBackend {
    override val settings = settingsRepository.settings
    private val settingsUpdateMutex = Mutex()
    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profilePrefs = context.getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE)
    private val profileAutoUpdateScheduler = ProfileAutoUpdateScheduler(context)
    private val profileAutoUpdateStateStore = ProfileAutoUpdateStateStore(context)
    private val profileUpdateCoordinator = ProfileUpdateCoordinator()
    private val profileCatalogCoordinator = ProfileCatalogCoordinator()
    private val profileCatalogStore = ProfileCatalogStore(profilePrefs, profileCatalogCoordinator)
    private val profileCatalogReader = ProfileCatalogReader(profileCatalogStore, profileAutoUpdateStateStore)
    private val profileStagingStore = ProfileStagingStore(
        profilePrefs = profilePrefs,
        filesDir = context.filesDir,
        catalogCoordinator = profileCatalogCoordinator,
        catalogStore = profileCatalogStore,
    )
    private val profileImportOperations = ProfileImportOperations(
        context = context,
        profileCatalogStore = profileCatalogStore,
        profileStagingStore = profileStagingStore,
        proxyPort = { Global.proxyPort },
    )
    private val profileStartupRecovery = ProfileStartupRecovery(
        profileStagingStore = profileStagingStore,
        filesDir = context.filesDir,
    )

    override val runtimeStatus = BackendRuntimeState.status
    override val serviceState: StateFlow<ServiceState> = BackendRuntimeState.serviceState
    override val serviceError: StateFlow<String?> = BackendRuntimeState.serviceError
    override val vpnSystemStatus: StateFlow<VpnSystemStatus> = BackendRuntimeState.vpnSystemStatus

    private val _activeProfile = MutableStateFlow<ProfileSummary?>(null)
    override val activeProfile: StateFlow<ProfileSummary?> = _activeProfile.asStateFlow()

    private val _runtimeError = MutableStateFlow<BackendRuntimeError?>(null)
    override val runtimeError: StateFlow<BackendRuntimeError?> = _runtimeError.asStateFlow()

    private val vpnOperations = BackendVpnOperations(
        context = context,
        serviceState = serviceState,
        profilePath = { Global.profilePath },
    )
    private val controllerOperations = BackendControllerOperations(
        socketPath = "${context.cacheDir}/clash.sock",
        serviceState = serviceState,
        notRunningMessage = {
            context.getString(rs.chimera.android.R.string.panel_not_running_message)
        },
        recordRuntimeError = ::recordRuntimeError,
        clearRuntimeError = ::clearRuntimeError,
    )
    private val runtimeTelemetry = RuntimeTelemetryObserver(
        scope = backendScope,
        serviceState = serviceState,
        appForeground = AppForegroundState.isForeground,
        fetchTraffic = controllerOperations::fetchTraffic,
        fetchMemory = controllerOperations::fetchMemory,
        fetchProxyGroups = controllerOperations::fetchProxyGroups,
        recordError = ::recordRuntimeError,
        clearError = ::clearRuntimeError,
    )
    override val traffic = runtimeTelemetry.traffic
    override val memoryInfo = runtimeTelemetry.memoryInfo
    override val proxyGroups = runtimeTelemetry.proxyGroups

    private val diagnosticsOperations = BackendDiagnosticsOperations(
        context = context,
        serviceState = serviceState,
        serviceError = serviceError,
        vpnSystemStatus = vpnSystemStatus,
        activeProfile = activeProfile,
        traffic = traffic,
        memoryInfo = memoryInfo,
        proxyGroups = proxyGroups,
        runtimeError = runtimeError,
    )

    private val initialization = BackendInitialization(backendScope) {
        profileStartupRecovery.recover()
        refreshActiveProfile()
    }

    init {
        backendScope.launch {
            awaitReady()
            synchronizeProfileAutoUpdateSchedule()
        }
        runtimeTelemetry.start()
    }

    override suspend fun awaitReady() = initialization.awaitReady()

    private suspend fun prepareStartVpnReady(): StartVpnResult =
        vpnOperations.prepareStartVpn()

    private suspend fun startVpnAfterPermissionReady() {
        vpnOperations.startVpnAfterPermission()
    }

    override suspend fun stopVpn() = withContext(Dispatchers.IO) {
        vpnOperations.stopVpn()
    }

    override suspend fun restartVpn() = withContext(Dispatchers.IO) {
        vpnOperations.restartVpn()
    }

    private suspend fun listProfilesReady(): List<ProfileSummary> {
        profileStagingStore.recoverDeletions()
        val profiles = profileCatalogReader.readProfiles()
        refreshProfileAutoUpdateSchedule(profiles)
        return profiles
    }

    override suspend fun activateProfile(id: String) {
        profileRecoveryReady.await()
        withContext(Dispatchers.IO) {
            profileCatalogCoordinator.withLock {
                val document = profileCatalogStore.readDocument()
                val updatedProfiles = ProfileCatalogPolicy.activate(document.entries, id)
                    ?: throw IllegalArgumentException("Profile not found")
                val activePath = requireNotNull(ProfileCatalogPolicy.activePath(updatedProfiles))
                profileCatalogStore.commitCatalog(
                    catalog = profileCatalogStore.render(document, updatedProfiles),
                    activePath = activePath,
                )
                try {
                    Global.restoreProfilePath()
                } finally {
                    refreshActiveProfile()
                }
            }
        }
        if (serviceState.value == ServiceState.RUNNING) {
            restartVpn()
        }
    }

    private suspend fun importLocalProfileReady(uri: Uri, name: String?): String {
        val (isFirst, resolvedName) = profileImportOperations.importLocalProfile(uri, name)
        if (isFirst) {
            restoreImportedActiveProfile()
        }
        return resolvedName
    }

    private suspend fun importRemoteProfileReady(
        request: RemoteProfileRequest,
        onProgress: (ProfileDownloadProgress) -> Unit,
    ): String {
        val (isFirst, resolvedName) = profileImportOperations.importRemoteProfile(request, onProgress)
        if (isFirst) {
            restoreImportedActiveProfile()
        }
        synchronizeProfileAutoUpdateSchedule()
        return resolvedName
    }

    private suspend fun deleteProfileReady(id: String) {
        profileUpdateCoordinator.withLock(id) {
            deleteProfileLocked(id)
        }
        synchronizeProfileAutoUpdateSchedule()
    }

    private fun deleteProfileLocked(id: String) {
        profileCatalogCoordinator.withLock {
            val document = profileCatalogStore.readDocument()
            val deletion = ProfileCatalogPolicy.delete(document.entries, id)
                ?: throw IllegalArgumentException("Profile not found")
            val activePath = ProfileCatalogPolicy.activePath(deletion.profiles)
            val originalCatalog = document.serialized
            val originalActivePath = ProfileCatalogPolicy.activePath(document.entries)
            val updatedCatalog = profileCatalogStore.render(document, deletion.profiles)
            ProfileDeletionPolicy.delete(
                file = File(deletion.deletedFilePath),
                shouldDeleteFile = deletion.shouldDeleteFile,
                persistDeletion = {
                    profileCatalogStore.commitCatalog(updatedCatalog, activePath)
                },
                rollbackCatalog = {
                    profileCatalogStore.commitCatalog(originalCatalog, originalActivePath)
                },
            )
            Global.restoreProfilePath()
            if (!profileAutoUpdateStateStore.clear(id)) {
                PrivacySafeLog.warningDetail(TAG, "Failed to clear deleted auto-update state", "profileId=$id")
            }
            refreshActiveProfile()
        }
    }

    private suspend fun renameProfileReady(id: String, newName: String) {
        profileUpdateCoordinator.withLock(id) {
            renameProfileLocked(id, newName)
        }
    }

    private fun renameProfileLocked(id: String, newName: String) {
        val normalizedName = newName.trim()
        require(normalizedName.isNotEmpty()) { "Profile name is empty" }
        profileCatalogCoordinator.withLock {
            val document = profileCatalogStore.readDocument()
            val updatedProfiles = ProfileCatalogPolicy.rename(
                document.entries,
                id,
                normalizedName,
            ) ?: throw IllegalArgumentException("Profile not found")
            profileCatalogStore.commitCatalog(
                catalog = profileCatalogStore.render(document, updatedProfiles),
                activePath = ProfileCatalogPolicy.activePath(updatedProfiles),
            )
            refreshActiveProfile()
        }
    }

    private suspend fun updateRemoteProfileReady(
        id: String,
        onProgress: (ProfileDownloadProgress) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            profileUpdateCoordinator.withLock(id) {
                updateRemoteProfileLocked(id, onProgress)
            }
        }
    }

    override suspend fun updateRemoteProfileSettings(id: String, settings: RemoteProfileSettings) {
        withContext(Dispatchers.IO) {
            profileUpdateCoordinator.withLock(id) {
                val normalized = ProfileRemotePolicy.normalizeSettings(settings)
                val current = profileCatalogStore.readRemoteProfile(id)
                profileCatalogStore.updateRemoteProfileSettings(id, normalized)
                if (ProfileRemotePolicy.invalidatesAutoUpdateState(current, normalized) &&
                    !profileAutoUpdateStateStore.clear(id)
                ) {
                    PrivacySafeLog.warningDetail(TAG, "Failed to clear edited auto-update state", "profileId=$id")
                }
                refreshActiveProfile()
                synchronizeProfileAutoUpdateSchedule()
            }
        }
    }

    private suspend fun updateRemoteProfileLocked(
        id: String,
        onProgress: (ProfileDownloadProgress) -> Unit,
    ) {
        val targetProfile = profileCatalogStore.readRemoteProfile(id)
        require(targetProfile.type == "REMOTE") { "Profile is not remote" }
        val url = targetProfile.url
            ?: throw IllegalStateException("Remote profile URL is missing")
        ProfileRemotePolicy.requireValidUrl(url)

        val userAgent = targetProfile.userAgent
        val proxyUrl = targetProfile.proxyUrl
            ?: Global.proxyPort?.let { "http://127.0.0.1:$it" }

        val outputFile = File(targetProfile.filePath)

        val updatedActiveProfile = withContext(Dispatchers.IO) {
            ProfileUpdateTransactionPolicy.run(
                destinationFile = outputFile,
                update = {
                    val tempFile = ProfileDownloadRecoveryPolicy.createStage(outputFile)
                    tempFile.delete()
                    try {
                        ChimeraFfi.ensureInitialized()
                        val result = downloadFileWithProgress(
                            url = url,
                            outputPath = tempFile.absolutePath,
                            userAgent = userAgent,
                            proxyUrl = proxyUrl,
                            progressCallback = object : DownloadProgressCallback {
                                override fun onProgress(progress: uniffi.chimera_ffi.DownloadProgress) {
                                    onProgress(ProfileDownloadProgress(progress.downloaded, progress.total))
                                }
                            },
                        )
                        if (!result.success) {
                            throw IllegalStateException(result.errorMessage ?: "Unknown download error")
                        }
                        ProfileImportPolicy.requireUsableDownloadedProfile(tempFile)
                        verifyConfig(tempFile.absolutePath)

                        tempFile
                    } catch (error: Throwable) {
                        tempFile.delete()
                        throw error
                    }
                },
                persistMetadata = { file, backup ->
                    profileCatalogStore.updateRemoteProfileMetadata(
                        id = id,
                        file = file,
                        backup = backup,
                        updatedAt = nextRemoteProfileCommitTimestamp(
                            previous = targetProfile.lastUpdated,
                            now = System.currentTimeMillis(),
                        ),
                    )
                },
                beginBackupTransaction = profileStagingStore::markUpdatePending,
                clearBackupTransaction = profileStagingStore::clearUpdatePending,
            )
        }
        if (!profileAutoUpdateStateStore.clear(id)) {
            PrivacySafeLog.warningDetail(TAG, "Failed to clear updated auto-update state", "profileId=$id")
        }
        try {
            if (updatedActiveProfile) Global.restoreProfilePath()
        } finally {
            refreshActiveProfile()
        }
    }

    private suspend fun verifyProfileReady(filePath: String): Result<String> =
        profileImportOperations.verifyProfile(filePath)

    override suspend fun listProxyGroups(): List<ProxyGroupSnapshot> =
        controllerOperations.listProxyGroups()

    override suspend fun selectProxy(groupName: String, proxyName: String) {
        controllerOperations.selectProxy(groupName, proxyName)
    }

    override suspend fun setMode(mode: ProxyMode) {
        controllerOperations.setMode(mode)
    }

    override suspend fun resetNetwork() {
        controllerOperations.resetNetwork()
    }

    override suspend fun testProxyDelay(proxyName: String): String =
        controllerOperations.testProxyDelay(proxyName)

    override suspend fun listConnections(): ConnectionsSnapshot =
        controllerOperations.listConnections()

    override suspend fun closeConnection(id: String) {
        controllerOperations.closeConnection(id)
    }

    override suspend fun closeAllConnections() {
        controllerOperations.closeAllConnections()
    }

    override suspend fun listRules(): List<RuleSnapshot> =
        controllerOperations.listRules()

    override suspend fun listProxyProviders(): List<ProxyProviderSnapshot> =
        controllerOperations.listProxyProviders()

    override suspend fun updateProxyProvider(name: String) {
        controllerOperations.updateProxyProvider(name)
    }

    override suspend fun healthcheckProxyProvider(name: String) {
        controllerOperations.healthcheckProxyProvider(name)
    }

    override suspend fun queryDns(name: String, recordType: String): String =
        controllerOperations.queryDns(name, recordType)

    override suspend fun readRuntimeLogs(maxLines: Int): String =
        diagnosticsOperations.readRuntimeLogs(maxLines)

    override suspend fun clearRuntimeLogs() {
        diagnosticsOperations.clearRuntimeLogs()
    }

    override suspend fun buildDiagnosticsBundle(): String =
        diagnosticsOperations.buildDiagnosticsBundle()

    override suspend fun updateSettings(patch: SettingsPatch): SettingsApplyEffect =
        withContext(Dispatchers.IO) {
            settingsUpdateMutex.withLock {
                val applyEffect = patch.requiredApplyEffect()
                settingsRepository.update(patch)
                if (applyEffect != SettingsApplyEffect.IMMEDIATE && serviceState.value == ServiceState.RUNNING) {
                    restartVpn()
                }
                applyEffect
            }
        }

    private fun refreshProfileAutoUpdateSchedule(profiles: List<ProfileSummary>) {
        runCatching { profileAutoUpdateScheduler.refresh(profiles) }
            .onFailure { error ->
                PrivacySafeLog.error(TAG, "Failed to synchronize automatic profile update schedule", error)
            }
    }

    private suspend fun synchronizeProfileAutoUpdateSchedule() {
        ProfileAutoUpdateScheduleSync.run(
            loadProfiles = ::listProfiles,
            refreshSchedule = ::refreshProfileAutoUpdateSchedule,
            onFailure = { error ->
                PrivacySafeLog.error(TAG, "Failed to refresh profile list after catalog mutation", error)
            },
        )
    }

    private fun recordRuntimeError(
        source: BackendRuntimeErrorSource,
        prefix: String,
        error: Throwable,
    ) {
        val detail = error.toUserVisibleMessage(
            Global.application,
            rs.chimera.android.R.string.profile_unknown_error,
        )
        _runtimeError.value = BackendRuntimeError(source, "$prefix: $detail")
    }

    private fun clearRuntimeError(source: BackendRuntimeErrorSource) {
        if (_runtimeError.value?.source == source) {
            _runtimeError.value = null
        }
    }

    private fun refreshActiveProfile() {
        _activeProfile.value = runCatching(profileCatalogReader::readActiveProfile).getOrNull()
    }

    private suspend fun restoreImportedActiveProfile() {
        withContext(Dispatchers.IO) {
            try {
                Global.restoreProfilePath()
            } finally {
                refreshActiveProfile()
            }
        }
    }

    override suspend fun prepareStartVpn(): StartVpnResult =
        withContext(Dispatchers.IO) {
            awaitReady()
            prepareStartVpnReady()
        }

    override suspend fun startVpnAfterPermission(): Unit =
        withContext(Dispatchers.IO) {
            awaitReady()
            startVpnAfterPermissionReady()
        }

    override suspend fun activateProfile(id: String): Unit =
        withContext(Dispatchers.IO) {
            awaitReady()
            activateProfileReady(id)
        }

    override suspend fun listProfiles(): List<ProfileSummary> =
        withContext(Dispatchers.IO) {
            awaitReady()
            listProfilesReady()
        }

    override suspend fun deleteProfile(id: String): Unit =
        withContext(Dispatchers.IO) {
            awaitReady()
            deleteProfileReady(id)
        }

    override suspend fun renameProfile(id: String, newName: String): Unit =
        withContext(Dispatchers.IO) {
            awaitReady()
            renameProfileReady(id, newName)
        }

    override suspend fun importLocalProfile(uri: Uri, name: String?): String =
        withContext(Dispatchers.IO) {
            awaitReady()
            importLocalProfileReady(uri, name)
        }

    override suspend fun importRemoteProfile(request: RemoteProfileRequest, onProgress: (ProfileDownloadProgress) -> Unit): String =
        withContext(Dispatchers.IO) {
            awaitReady()
            importRemoteProfileReady(request, onProgress)
        }

    override suspend fun updateRemoteProfile(id: String, onProgress: (ProfileDownloadProgress) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            awaitReady()
            updateRemoteProfileReady(id, onProgress)
        }

    override suspend fun verifyProfile(filePath: String): Result<String> =
        withContext(Dispatchers.IO) {
            awaitReady()
            verifyProfileReady(filePath)
        }

    private companion object {
        const val TAG = "ChimeraBackend"
        const val FILE_PREFS = "file_prefs"
    }
}
