package rs.chimera.android.backend

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rs.chimera.android.Global
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ConnectionsSnapshot
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyProviderSnapshot
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.backend.model.RuleSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.SettingsApplyEffect
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.backend.model.TrafficSnapshot
import rs.chimera.android.backend.model.VpnSystemStatus
import rs.chimera.android.ffi.ChimeraFfi
import rs.chimera.android.ffi.shutdownClash
import rs.chimera.android.service.TunService
import rs.chimera.android.service.VpnDesiredStateReason
import rs.chimera.android.service.VpnDesiredStateStore
import rs.chimera.android.service.VpnRuntimeRegistry
import rs.chimera.android.util.PrivacySafeLog
import uniffi.chimera_ffi.ClashController
import uniffi.chimera_ffi.DownloadProgress
import uniffi.chimera_ffi.DownloadProgressCallback
import uniffi.chimera_ffi.Mode
import uniffi.chimera_ffi.downloadFileWithProgress
import uniffi.chimera_ffi.verifyConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChimeraBackendImpl : ChimeraBackend {
    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vpnOperationMutex = Mutex()
    private val controller by lazy { ClashController("${Global.application.cacheDir}/clash.sock") }
    private val profilePrefs = Global.application.getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE)
    private val settingsPrefs = Global.application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val profileAutoUpdateScheduler = ProfileAutoUpdateScheduler(Global.application)
    private val profileAutoUpdateStateStore = ProfileAutoUpdateStateStore(Global.application)
    private val vpnDesiredStateStore = VpnDesiredStateStore(Global.application)
    private val profileUpdateCoordinator = ProfileUpdateCoordinator()
    private val profileCatalogCoordinator = ProfileCatalogCoordinator()
    private val profileCatalogStore = ProfileCatalogStore(profilePrefs, profileCatalogCoordinator)
    private val profileCatalogReader = ProfileCatalogReader(profileCatalogStore, profileAutoUpdateStateStore)
    private val profileStagingStore = ProfileStagingStore(
        profilePrefs = profilePrefs,
        filesDir = Global.application.filesDir,
        catalogCoordinator = profileCatalogCoordinator,
        catalogStore = profileCatalogStore,
    )

    override val serviceState: StateFlow<ServiceState> = BackendRuntimeState.serviceState
    override val serviceError: StateFlow<String?> = BackendRuntimeState.serviceError
    override val vpnSystemStatus: StateFlow<VpnSystemStatus> = BackendRuntimeState.vpnSystemStatus

    private val _activeProfile = MutableStateFlow<ProfileSummary?>(null)
    override val activeProfile: StateFlow<ProfileSummary?> = _activeProfile.asStateFlow()

    private val _runtimeError = MutableStateFlow<BackendRuntimeError?>(null)
    override val runtimeError: StateFlow<BackendRuntimeError?> = _runtimeError.asStateFlow()

    private val runtimeTelemetry = RuntimeTelemetryObserver(
        scope = backendScope,
        serviceState = serviceState,
        appForeground = AppForegroundState.isForeground,
        fetchTraffic = {
            controller.getConnectionSummary().let { summary ->
                TrafficSnapshot(
                    downloadTotal = summary.downloadTotal,
                    uploadTotal = summary.uploadTotal,
                    connectionCount = summary.connectionCount,
                )
            }
        },
        fetchMemory = {
            controller.getMemory().let { response ->
                MemoryInfo(
                    inUse = response.inuse,
                    osLimit = response.oslimit,
                )
            }
        },
        fetchProxyGroups = ::fetchProxyGroupsFromController,
        recordError = ::recordRuntimeError,
        clearError = ::clearRuntimeError,
    )
    override val traffic = runtimeTelemetry.traffic
    override val memoryInfo = runtimeTelemetry.memoryInfo
    override val proxyGroups = runtimeTelemetry.proxyGroups

    init {
        runCatching { profileStagingStore.recoverImports() }
            .onFailure { error ->
                PrivacySafeLog.error(TAG, "Failed to recover staged profile imports", error)
            }
        runCatching { profileStagingStore.recoverBackups() }
            .onFailure { error ->
                PrivacySafeLog.error(TAG, "Failed to recover staged profile backups", error)
            }
        runCatching { profileStagingStore.recoverDeletions() }
            .onFailure { error ->
                PrivacySafeLog.error(TAG, "Failed to recover staged profile deletions", error)
            }
        runCatching { ProfileDownloadRecoveryPolicy.cleanup(Global.application.filesDir) }
            .onFailure { error ->
                PrivacySafeLog.error(TAG, "Failed to recover staged profile downloads", error)
            }
        refreshActiveProfile()
        backendScope.launch {
            synchronizeProfileAutoUpdateSchedule()
        }
        runtimeTelemetry.start()
    }

    override suspend fun prepareStartVpn(): StartVpnResult {
        val context = Global.application
        val path = Global.profilePath
        if (path.isBlank()) {
            return StartVpnResult.Error(context.getString(rs.chimera.android.R.string.service_profile_required))
        }

        val intent = VpnService.prepare(context)
        return if (intent != null) {
            StartVpnResult.Prepared(intent)
        } else {
            StartVpnResult.PermissionNotRequired
        }
    }

    override suspend fun startVpnAfterPermission() {
        vpnDesiredStateStore.markRunning()
        VpnRuntimeRegistry.requestStart()
        BackendRuntimeState.updateServiceState(ServiceState.STARTING)
        try {
            ContextCompat.startForegroundService(
                Global.application,
                Intent(Global.application, TunService::class.java),
            )
        } catch (error: Exception) {
            runCatching { vpnDesiredStateStore.markStopped(VpnDesiredStateReason.START_FAILED) }
                .onFailure(error::addSuppressed)
            VpnRuntimeRegistry.requestStop()
            BackendRuntimeState.updateServiceError(error.messageOrType())
            throw error
        }
    }

    override suspend fun stopVpn() {
        val desiredStateError =
            runCatching { vpnDesiredStateStore.markStopped(VpnDesiredStateReason.USER_STOP) }
                .exceptionOrNull()
        VpnRuntimeRegistry.requestStop()
        vpnOperationMutex.withLock {
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
        if (desiredStateError != null && vpnDesiredStateStore.snapshot().shouldRun) {
            BackendRuntimeState.updateServiceError(desiredStateError.messageOrType())
            throw desiredStateError
        }
    }

    override suspend fun restartVpn() {
        check(vpnOperationMutex.tryLock()) { "Another VPN operation is already in progress" }
        try {
            check(serviceState.value == ServiceState.RUNNING) { "VPN is not running" }
            VpnRuntimeRegistry.restartVpn()
        } finally {
            vpnOperationMutex.unlock()
        }
    }

    override suspend fun listProfiles(): List<ProfileSummary> {
        profileStagingStore.recoverDeletions()
        val profiles = profileCatalogReader.readProfiles()
        refreshProfileAutoUpdateSchedule(profiles)
        return profiles
    }

    override suspend fun activateProfile(id: String) {
        profileCatalogCoordinator.withLock {
            val document = profileCatalogStore.readDocument()
            val updatedProfiles = ProfileCatalogPolicy.activate(document.entries, id)
                ?: throw IllegalArgumentException("Profile not found: $id")
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
        if (serviceState.value == ServiceState.RUNNING) {
            restartVpn()
        }
    }

    override suspend fun importLocalProfile(uri: Uri, name: String?) {
        val context = Global.application
        val fileName = queryDisplayName(context, uri)
        val safeName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.')
        val id = UUID.randomUUID().toString()
        val destinationFile = File(
            context.filesDir,
            ProfileRemotePolicy.storageFileName(id, fileName),
        )
        val stagedFile = ProfileImportRecoveryPolicy.createStage(destinationFile)

        withContext(Dispatchers.IO) {
            ProfileFilePolicy.writeOrRollback(stagedFile) { target ->
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to open selected profile")
                input.use {
                    target.outputStream().use { output ->
                        ProfileImportPolicy.copyWithLimit(it, output)
                    }
                }
            }
        }

        val profileJson = org.json.JSONObject()
        profileJson.put("id", id)
        profileJson.put("name", safeName)
        profileJson.put("filePath", destinationFile.absolutePath)
        profileJson.put("createdAt", System.currentTimeMillis())
        profileJson.put("isActive", false)
        profileJson.put("fileSize", stagedFile.length())
        profileJson.put("type", rs.chimera.android.model.ProfileType.LOCAL.name)

        val becameActive = ProfileImportTransactionPolicy.run(
            stagedFile = stagedFile,
            destinationFile = destinationFile,
            beginImportTransaction = profileStagingStore::markImportPending,
            persistMetadata = { file ->
                profileJson.put("filePath", file.absolutePath)
                profileJson.put("fileSize", file.length())
                profileCatalogStore.append(profileJson, pendingImport = file)
            },
            clearImportTransaction = profileStagingStore::clearImportPending,
        )
        if (becameActive) {
            try {
                Global.restoreProfilePath()
            } finally {
                refreshActiveProfile()
            }
        }
    }

    override suspend fun importRemoteProfile(
        request: RemoteProfileRequest,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        ProfileRemotePolicy.requireValidUrl(request.url)
        val context = Global.application
        val resolvedName = request.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(Date())

        val id = UUID.randomUUID().toString()
        val destinationFile = File(
            context.filesDir,
            ProfileRemotePolicy.storageFileNameForUrl(id, request.url),
        )
        val stagedFile = ProfileImportRecoveryPolicy.createStage(destinationFile)
        withContext(Dispatchers.IO) {
            downloadProfileToFile(stagedFile, request, onProgress)
        }

        val profileJson = org.json.JSONObject()
        profileJson.put("id", id)
        profileJson.put("name", resolvedName)
        profileJson.put("filePath", destinationFile.absolutePath)
        profileJson.put("createdAt", System.currentTimeMillis())
        profileJson.put("isActive", false)
        profileJson.put("fileSize", stagedFile.length())
        profileJson.put("type", rs.chimera.android.model.ProfileType.REMOTE.name)
        profileJson.put("url", request.url)
        profileJson.put("lastUpdated", System.currentTimeMillis())
        profileJson.put("autoUpdate", request.autoUpdate)
        if (request.userAgent != null) profileJson.put("userAgent", request.userAgent)
        if (request.proxyUrl != null) profileJson.put("proxyUrl", request.proxyUrl)

        val becameActive = ProfileImportTransactionPolicy.run(
            stagedFile = stagedFile,
            destinationFile = destinationFile,
            beginImportTransaction = profileStagingStore::markImportPending,
            persistMetadata = { file ->
                profileJson.put("filePath", file.absolutePath)
                profileJson.put("fileSize", file.length())
                profileCatalogStore.append(profileJson, pendingImport = file)
            },
            clearImportTransaction = profileStagingStore::clearImportPending,
        )
        if (becameActive) {
            try {
                Global.restoreProfilePath()
            } finally {
                refreshActiveProfile()
            }
        }
        synchronizeProfileAutoUpdateSchedule()
    }

    override suspend fun deleteProfile(id: String) {
        profileUpdateCoordinator.withLock(id) {
            deleteProfileLocked(id)
        }
        synchronizeProfileAutoUpdateSchedule()
    }

    private fun deleteProfileLocked(id: String) {
        profileCatalogCoordinator.withLock {
            val document = profileCatalogStore.readDocument()
            val deletion = ProfileCatalogPolicy.delete(document.entries, id)
                ?: throw IllegalArgumentException("Profile not found: $id")
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
                Log.w(TAG, "Failed to clear auto-update state for deleted profile $id")
            }
            refreshActiveProfile()
        }
    }

    override suspend fun renameProfile(id: String, newName: String) {
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
            ) ?: throw IllegalArgumentException("Profile not found: $id")
            profileCatalogStore.commitCatalog(
                catalog = profileCatalogStore.render(document, updatedProfiles),
                activePath = ProfileCatalogPolicy.activePath(updatedProfiles),
            )
            refreshActiveProfile()
        }
    }

    override suspend fun updateRemoteProfile(
        id: String,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        profileUpdateCoordinator.withLock(id) {
            updateRemoteProfileLocked(id, onProgress)
        }
    }

    private suspend fun updateRemoteProfileLocked(
        id: String,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val targetProfile = profileCatalogStore.readRemoteProfile(id)
        require(targetProfile.type == "REMOTE") { "Profile is not remote: $id" }
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
                                override fun onProgress(progress: DownloadProgress) {
                                    onProgress(progress)
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
                        updatedAt = System.currentTimeMillis(),
                    )
                },
                beginBackupTransaction = profileStagingStore::markUpdatePending,
                clearBackupTransaction = profileStagingStore::clearUpdatePending,
            )
        }
        if (!profileAutoUpdateStateStore.clear(id)) {
            Log.w(TAG, "Failed to clear auto-update state for updated profile $id")
        }
        try {
            if (updatedActiveProfile) Global.restoreProfilePath()
        } finally {
            refreshActiveProfile()
        }
    }

    override suspend fun verifyProfile(filePath: String): Result<String> {
        return runCatching {
            ChimeraFfi.ensureInitialized()
            verifyConfig(filePath)
        }
    }

    override suspend fun listProxyGroups(): List<ProxyGroupSnapshot> =
        runProxyOperation("Failed to refresh proxy groups") {
            fetchProxyGroupsFromController()
        }

    override suspend fun selectProxy(groupName: String, proxyName: String) {
        runProxyOperation("Failed to select proxy") {
            controller.selectProxy(groupName, proxyName)
        }
    }

    override suspend fun setMode(mode: uniffi.chimera_ffi.Mode) {
        runProxyOperation("Failed to switch proxy mode") {
            controller.setMode(mode)
        }
    }

    override suspend fun resetNetwork() {
        runProxyOperation("Failed to reset network state") {
            controller.resetNetwork()
        }
    }

    override suspend fun testProxyDelay(proxyName: String): String =
        runProxyOperation("Failed to test proxy delay") {
            val response = controller.getProxyDelay(proxyName, null, null)
            "${response.delay}ms"
        }

    override suspend fun listConnections(): ConnectionsSnapshot =
        runConnectionOperation("Failed to refresh connections", ::fetchConnectionsFromController)

    override suspend fun closeConnection(id: String) {
        runConnectionOperation("Failed to close connection") {
            controller.closeConnection(id)
        }
    }

    override suspend fun closeAllConnections() {
        runConnectionOperation("Failed to close all connections") {
            controller.closeAllConnections()
        }
    }

    override suspend fun listRules(): List<RuleSnapshot> {
        requireProxyServiceRunning()
        return controller.getRules().map { rule ->
            RuleSnapshot(
                type = rule.ruleType,
                proxy = rule.proxy,
                payload = rule.payload,
            )
        }
    }

    override suspend fun listProxyProviders(): List<ProxyProviderSnapshot> {
        requireProxyServiceRunning()
        return controller.getProxyProviders().map { provider ->
            ProxyProviderSnapshot(
                name = provider.name,
                type = provider.providerType,
                vehicleType = provider.vehicleType,
                proxyCount = provider.proxyCount,
            )
        }
    }

    override suspend fun updateProxyProvider(name: String) {
        requireProxyServiceRunning()
        controller.updateProxyProvider(name)
    }

    override suspend fun healthcheckProxyProvider(name: String) {
        requireProxyServiceRunning()
        controller.healthcheckProxyProvider(name)
    }

    override suspend fun queryDns(name: String, recordType: String): String {
        requireProxyServiceRunning()
        return controller.queryDns(name, recordType)
    }

    override suspend fun readRuntimeLogs(maxLines: Int): String {
        return Global.readRuntimeLogTail(maxLines)
    }

    override suspend fun clearRuntimeLogs() {
        Global.clearRuntimeLog()
    }

    override suspend fun updateSettings(patch: SettingsPatch): SettingsApplyEffect {
        val applyEffect = patch.requiredApplyEffect()
        settingsPrefs.edit {
            patch.allowLan?.let { putBoolean("allow_lan", it) }
            patch.mixedPort?.let { putInt("mixed_port", it.toInt()) }
            if (patch.clearHttpPort) remove("http_port")
            patch.httpPort?.let { putInt("http_port", it.toInt()) }
            if (patch.clearSocksPort) remove("socks_port")
            patch.socksPort?.let { putInt("socks_port", it.toInt()) }
            patch.fakeIp?.let { putBoolean("fake_ip", it) }
            patch.ipv6?.let { putBoolean("ipv6", it) }
            patch.appFilterMode?.let { putString("app_filter_mode", it) }
            patch.allowedApps?.let { putStringSet("allowed_apps", it) }
            patch.disallowedApps?.let { putStringSet("disallowed_apps", it) }
        }
        if (applyEffect != SettingsApplyEffect.IMMEDIATE && serviceState.value == ServiceState.RUNNING) {
            restartVpn()
        }
        return applyEffect
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
        _runtimeError.value = BackendRuntimeError(source, "$prefix: ${error.messageOrType()}")
    }

    private fun Throwable.messageOrType(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private fun clearRuntimeError(source: BackendRuntimeErrorSource) {
        if (_runtimeError.value?.source == source) {
            _runtimeError.value = null
        }
    }

    private fun requireProxyServiceRunning() {
        RuntimeOperationPolicy.requireRunning(serviceState.value) {
            Global.application.getString(rs.chimera.android.R.string.panel_not_running_message)
        }
    }

    private suspend fun <T> runProxyOperation(
        errorPrefix: String,
        operation: suspend () -> T,
    ): T {
        requireProxyServiceRunning()
        return try {
            operation().also { clearRuntimeError(BackendRuntimeErrorSource.PROXY_GROUPS) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recordRuntimeError(
                source = BackendRuntimeErrorSource.PROXY_GROUPS,
                prefix = errorPrefix,
                error = error,
            )
            throw error
        }
    }

    private suspend fun <T> runConnectionOperation(
        errorPrefix: String,
        operation: suspend () -> T,
    ): T {
        RuntimeOperationPolicy.requireRunning(serviceState.value) {
            Global.application.getString(rs.chimera.android.R.string.panel_not_running_message)
        }
        return try {
            operation().also { clearRuntimeError(BackendRuntimeErrorSource.TRAFFIC) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recordRuntimeError(
                source = BackendRuntimeErrorSource.TRAFFIC,
                prefix = errorPrefix,
                error = error,
            )
            throw error
        }
    }

    private suspend fun fetchProxyGroupsFromController(): List<ProxyGroupSnapshot> {
        val mode = controller.getMode() ?: Mode.RULE
        return controller.getProxies().toProxyGroupSnapshots(mode)
    }

    private suspend fun fetchConnectionsFromController(): ConnectionsSnapshot =
        controller.getConnections().toConnectionsSnapshot()

    private fun refreshActiveProfile() {
        _activeProfile.value = runCatching(profileCatalogReader::readActiveProfile).getOrNull()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: "remote-profile.yaml"
    }

    private suspend fun downloadProfileToFile(
        file: File,
        request: RemoteProfileRequest,
        onProgress: (DownloadProgress) -> Unit,
    ): File {
        return try {
            ChimeraFfi.ensureInitialized()
            val result = downloadFileWithProgress(
                url = request.url,
                outputPath = file.absolutePath,
                userAgent = request.userAgent,
                proxyUrl = request.proxyUrl ?: Global.proxyPort?.let { "http://127.0.0.1:$it" },
                progressCallback = object : DownloadProgressCallback {
                    override fun onProgress(progress: DownloadProgress) {
                        onProgress(progress)
                    }
                },
            )

            check(result.success) {
                result.errorMessage ?: "Unknown download error"
            }
            ProfileImportPolicy.requireUsableDownloadedProfile(file)
            verifyConfig(file.absolutePath)
            file
        } catch (error: Throwable) {
            ProfileFilePolicy.deleteAfterFailure(file, error)
            throw error
        }
    }

    private companion object {
        const val TAG = "ChimeraBackend"
        const val FILE_PREFS = "file_prefs"
    }
}
