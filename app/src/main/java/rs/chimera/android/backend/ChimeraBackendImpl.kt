package rs.chimera.android.backend

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import rs.chimera.android.Global
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ConnectionSnapshot
import rs.chimera.android.backend.model.ConnectionsSnapshot
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ProxyDelayHistory
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxySnapshot
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.backend.model.TrafficSnapshot
import rs.chimera.android.ffi.ChimeraFfi
import rs.chimera.android.ffi.shutdownClash
import rs.chimera.android.service.TunService
import rs.chimera.android.service.tunService
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
    private val controller by lazy { ClashController("${Global.application.cacheDir}/clash.sock") }
    private val profilePrefs = Global.application.getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE)
    private val settingsPrefs = Global.application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override val serviceState: StateFlow<ServiceState> = BackendRuntimeState.serviceState

    private val _activeProfile = MutableStateFlow<ProfileSummary?>(null)
    override val activeProfile: StateFlow<ProfileSummary?> = _activeProfile.asStateFlow()

    private val _traffic = MutableStateFlow(TrafficSnapshot(0, 0, 0))
    override val traffic: StateFlow<TrafficSnapshot> = _traffic.asStateFlow()

    private val _memoryInfo = MutableStateFlow(MemoryInfo(0, 0))
    override val memoryInfo: StateFlow<MemoryInfo> = _memoryInfo.asStateFlow()

    private val _proxyGroups = MutableStateFlow<List<ProxyGroupSnapshot>>(emptyList())
    override val proxyGroups: StateFlow<List<ProxyGroupSnapshot>> = _proxyGroups.asStateFlow()

    private val _connections = MutableStateFlow(ConnectionsSnapshot(emptyList(), 0, 0))
    override val connections: StateFlow<ConnectionsSnapshot> = _connections.asStateFlow()

    private val _runtimeError = MutableStateFlow<BackendRuntimeError?>(null)
    override val runtimeError: StateFlow<BackendRuntimeError?> = _runtimeError.asStateFlow()

    init {
        refreshActiveProfile()
        observeTraffic()
        observeMemory()
        observeProxyGroups()
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
        BackendRuntimeState.updateServiceState(ServiceState.STARTING)
        runCatching {
            ContextCompat.startForegroundService(
                Global.application,
                Intent(Global.application, TunService::class.java),
            )
        }.onFailure {
            BackendRuntimeState.updateServiceState(ServiceState.ERROR)
        }.getOrThrow()
    }

    override suspend fun stopVpn() {
        BackendRuntimeState.updateServiceState(ServiceState.STOPPING)
        val service = tunService
        if (service != null) {
            service.stopVpn()
        } else {
            shutdownClash()
            BackendRuntimeState.updateServiceState(ServiceState.STOPPED)
        }
    }

    override suspend fun listProfiles(): List<ProfileSummary> {
        val profilesJson = profilePrefs.getString(PROFILES_LIST_KEY, null) ?: return emptyList()
        val jsonArray = JSONArray(profilesJson)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                add(
                    ProfileSummary(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        filePath = obj.getString("filePath"),
                        type = when (obj.optString("type", rs.chimera.android.model.ProfileType.LOCAL.name)) {
                            "REMOTE" -> ProfileType.REMOTE
                            else -> ProfileType.LOCAL
                        },
                        isActive = obj.getBoolean("isActive"),
                        isRemote = obj.optString("type", rs.chimera.android.model.ProfileType.LOCAL.name) == "REMOTE",
                        lastUpdated = obj.takeIf { it.has("lastUpdated") }?.getLong("lastUpdated"),
                        fileSize = obj.getLong("fileSize"),
                        url = obj.optString("url").takeIf { it.isNotBlank() },
                        autoUpdate = obj.optBoolean("autoUpdate", false),
                        userAgent = obj.optString("userAgent").takeIf { it.isNotBlank() },
                        proxyUrl = obj.optString("proxyUrl").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
    }

    override suspend fun activateProfile(id: String) {
        val jsonArray = profileCatalogJson()
        val updatedProfiles = ProfileCatalogPolicy.activate(jsonArray.toCatalogEntries(), id)
            ?: throw IllegalArgumentException("Profile not found: $id")
        for (index in 0 until jsonArray.length()) {
            jsonArray.getJSONObject(index).put("isActive", updatedProfiles[index].isActive)
        }
        val activePath = requireNotNull(ProfileCatalogPolicy.activePath(updatedProfiles))
        profilePrefs.edit {
            putString(PROFILES_LIST_KEY, jsonArray.toString())
            putString(PROFILE_PATH_KEY, activePath)
        }
        Global.updateProfilePath(activePath)
        refreshActiveProfile()
    }

    override suspend fun importLocalProfile(uri: Uri, name: String?) {
        val context = Global.application
        val fileName = queryDisplayName(context, uri)
        val safeName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.')
        val id = UUID.randomUUID().toString()
        val extension = fileName.substringAfterLast('.', "yaml")
        val file = File(context.filesDir, profileStorageFileName(id, extension))

        withContext(Dispatchers.IO) {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Unable to open selected profile")
            input.use {
                file.outputStream().use { output ->
                    it.copyTo(output)
                }
            }
        }

        val profileJson = org.json.JSONObject()
        profileJson.put("id", id)
        profileJson.put("name", safeName)
        profileJson.put("filePath", file.absolutePath)
        profileJson.put("createdAt", System.currentTimeMillis())
        profileJson.put("isActive", false)
        profileJson.put("fileSize", file.length())
        profileJson.put("type", rs.chimera.android.model.ProfileType.LOCAL.name)

        appendProfile(profileJson)
    }

    override suspend fun importRemoteProfile(request: RemoteProfileRequest) {
        validateRemoteProfileUrl(request.url)
        val context = Global.application
        val resolvedName = request.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(Date())

        val id = UUID.randomUUID().toString()
        val file = withContext(Dispatchers.IO) {
            downloadProfileToAppDirectory(context, id, request)
        }

        val profileJson = org.json.JSONObject()
        profileJson.put("id", id)
        profileJson.put("name", resolvedName)
        profileJson.put("filePath", file.absolutePath)
        profileJson.put("createdAt", System.currentTimeMillis())
        profileJson.put("isActive", false)
        profileJson.put("fileSize", file.length())
        profileJson.put("type", rs.chimera.android.model.ProfileType.REMOTE.name)
        profileJson.put("url", request.url)
        profileJson.put("lastUpdated", System.currentTimeMillis())
        profileJson.put("autoUpdate", request.autoUpdate)
        if (request.userAgent != null) profileJson.put("userAgent", request.userAgent)
        if (request.proxyUrl != null) profileJson.put("proxyUrl", request.proxyUrl)

        appendProfile(profileJson)
    }

    override suspend fun deleteProfile(id: String) {
        val jsonArray = profileCatalogJson()
        val deletion = ProfileCatalogPolicy.delete(jsonArray.toCatalogEntries(), id)
            ?: throw IllegalArgumentException("Profile not found: $id")
        val updatedArray = JSONArray()
        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            deletion.profiles.firstOrNull { it.id == obj.getString("id") }?.let { updated ->
                obj.put("isActive", updated.isActive)
                updatedArray.put(obj)
            }
        }
        val activePath = ProfileCatalogPolicy.activePath(deletion.profiles)
        profilePrefs.edit {
            putString(PROFILES_LIST_KEY, updatedArray.toString())
            if (activePath == null) remove(PROFILE_PATH_KEY) else putString(PROFILE_PATH_KEY, activePath)
        }
        Global.updateProfilePath(activePath.orEmpty())
        if (deletion.shouldDeleteFile) {
            val file = File(deletion.deletedFilePath)
            check(!file.exists() || file.delete()) { "Failed to delete profile file: ${file.name}" }
        }
        refreshActiveProfile()
    }

    override suspend fun renameProfile(id: String, newName: String) {
        val normalizedName = newName.trim()
        require(normalizedName.isNotEmpty()) { "Profile name is empty" }
        val jsonArray = profileCatalogJson()
        val updatedProfiles = ProfileCatalogPolicy.rename(
            jsonArray.toCatalogEntries(),
            id,
            normalizedName,
        ) ?: throw IllegalArgumentException("Profile not found: $id")
        for (index in 0 until jsonArray.length()) {
            jsonArray.getJSONObject(index).put("name", updatedProfiles[index].name)
        }
        profilePrefs.edit { putString(PROFILES_LIST_KEY, jsonArray.toString()) }
        refreshActiveProfile()
    }

    override suspend fun updateRemoteProfile(id: String) {
        val jsonArray = profileCatalogJson()
        val targetProfile = (0 until jsonArray.length())
            .map { jsonArray.getJSONObject(it) }
            .firstOrNull { it.getString("id") == id }
            ?: throw IllegalArgumentException("Profile not found: $id")
        require(targetProfile.optString("type", "LOCAL") == "REMOTE") {
            "Profile is not remote: $id"
        }
        val url = targetProfile.optString("url").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Remote profile URL is missing")
        validateRemoteProfileUrl(url)

        val context = Global.application
        val userAgent = targetProfile.optString("userAgent").takeIf { it.isNotBlank() }
        val proxyUrl = targetProfile.optString("proxyUrl").takeIf { it.isNotBlank() }
            ?: Global.proxyPort?.let { "http://127.0.0.1:$it" }

        val file = withContext(Dispatchers.IO) {
            val outputFile = File(targetProfile.getString("filePath"))
            val tempFile = File(outputFile.parentFile ?: context.filesDir, "${outputFile.name}.download")
            tempFile.delete()

            ChimeraFfi.ensureInitialized()
            val result = downloadFileWithProgress(
                url = url,
                outputPath = tempFile.absolutePath,
                userAgent = userAgent,
                proxyUrl = proxyUrl,
                progressCallback = object : DownloadProgressCallback {
                    override fun onProgress(progress: DownloadProgress) {}
                },
            )
            if (!result.success) {
                tempFile.delete()
                throw IllegalStateException(result.errorMessage ?: "Unknown download error")
            }

            if (!tempFile.renameTo(outputFile)) {
                tempFile.copyTo(outputFile, overwrite = true)
                tempFile.delete()
            }
            outputFile
        }

        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            if (obj.getString("id") == id) {
                obj.put("filePath", file.absolutePath)
                obj.put("fileSize", file.length())
                obj.put("lastUpdated", System.currentTimeMillis())
            }
        }
        profilePrefs.edit {
            putString(PROFILES_LIST_KEY, jsonArray.toString())
            val activeProfile = (0 until jsonArray.length())
                .map { jsonArray.getJSONObject(it) }
                .firstOrNull { it.getBoolean("isActive") }
            activeProfile?.getString("filePath")?.let { putString(PROFILE_PATH_KEY, it) }
            if (activeProfile?.getString("id") == id) {
                Global.updateProfilePath(file.absolutePath)
            }
        }
        refreshActiveProfile()
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

    override suspend fun testProxyDelay(proxyName: String): String =
        runProxyOperation("Failed to test proxy delay") {
            val response = controller.getProxyDelay(proxyName, null, null)
            "${response.delay}ms"
        }

    override suspend fun listConnections(): ConnectionsSnapshot {
        if (serviceState.value != ServiceState.RUNNING) {
            return ConnectionsSnapshot(emptyList(), 0, 0)
        }

        return runCatching {
            val response = controller.getConnections()
            ConnectionsSnapshot(
                connections = response.connections.map { conn ->
                    ConnectionSnapshot(
                        id = conn.id,
                        host = conn.metadata.host,
                        process = null,
                        upload = conn.upload,
                        download = conn.download,
                        startTime = conn.start.toLongOrNull() ?: 0L,
                        chains = conn.chains,
                        rule = conn.rule,
                        metadata = mapOf(
                            "network" to conn.metadata.network,
                            "type" to conn.metadata.metadataType,
                            "sourceIp" to conn.metadata.sourceIp,
                            "destinationIp" to (conn.metadata.destinationIp ?: ""),
                            "sourcePort" to (conn.metadata.sourcePort?.toString() ?: ""),
                            "destinationPort" to conn.metadata.destinationPort.toString(),
                        ),
                    )
                },
                downloadTotal = response.downloadTotal,
                uploadTotal = response.uploadTotal,
            )
        }.getOrDefault(ConnectionsSnapshot(emptyList(), 0, 0))
    }

    override suspend fun readRuntimeLogs(maxLines: Int): String {
        return Global.readRuntimeLogTail(maxLines)
    }

    override suspend fun clearRuntimeLogs() {
        Global.clearRuntimeLog()
    }

    override suspend fun updateSettings(patch: SettingsPatch) {
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
    }

    private fun recordRuntimeError(
        source: BackendRuntimeErrorSource,
        prefix: String,
        error: Throwable,
    ) {
        val details = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
        _runtimeError.value = BackendRuntimeError(source, "$prefix: $details")
    }

    private fun clearRuntimeError(source: BackendRuntimeErrorSource) {
        if (_runtimeError.value?.source == source) {
            _runtimeError.value = null
        }
    }

    private fun requireProxyServiceRunning() {
        check(serviceState.value == ServiceState.RUNNING) {
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

    private suspend fun fetchProxyGroupsFromController(): List<ProxyGroupSnapshot> {
        val proxies = controller.getProxies()
        val mode = controller.getMode() ?: Mode.RULE
        val proxyMap = proxies.associate { proxy ->
            proxy.name to ProxySnapshot(
                name = proxy.name,
                type = proxy.proxyType,
                history = proxy.history.map { history ->
                    ProxyDelayHistory(
                        delay = history.delay,
                        time = history.time.toLongOrNull() ?: 0L,
                    )
                },
            )
        }
        return proxies.map { proxy ->
            ProxyGroupSnapshot(
                name = proxy.name,
                proxies = proxy.all,
                selected = proxy.now,
                mode = mode,
                proxyDetails = proxyMap,
            )
        }
    }

    private fun observeTraffic() {
        backendScope.launch {
            serviceState.collectLatest { state ->
                if (state != ServiceState.RUNNING) {
                    _traffic.value = TrafficSnapshot(0, 0, 0)
                    _connections.value = ConnectionsSnapshot(emptyList(), 0, 0)
                    clearRuntimeError(BackendRuntimeErrorSource.TRAFFIC)
                    return@collectLatest
                }

                delay(1000)
                while (true) {
                    runCatching {
                        controller.getConnections()
                    }.onSuccess { response ->
                        clearRuntimeError(BackendRuntimeErrorSource.TRAFFIC)
                        _traffic.value = TrafficSnapshot(
                            downloadTotal = response.downloadTotal,
                            uploadTotal = response.uploadTotal,
                            connectionCount = response.connections.size,
                        )
                        _connections.value = ConnectionsSnapshot(
                            connections = response.connections.map { conn ->
                                ConnectionSnapshot(
                                    id = conn.id,
                                    host = conn.metadata.host,
                                    process = null,
                                    upload = conn.upload,
                                    download = conn.download,
                                    startTime = conn.start.toLongOrNull() ?: 0L,
                                    chains = conn.chains,
                                    rule = conn.rule,
                                    metadata = mapOf(
                                        "network" to conn.metadata.network,
                                        "type" to conn.metadata.metadataType,
                                        "sourceIp" to conn.metadata.sourceIp,
                                        "destinationIp" to (conn.metadata.destinationIp ?: ""),
                                        "sourcePort" to (conn.metadata.sourcePort?.toString() ?: ""),
                                        "destinationPort" to conn.metadata.destinationPort.toString(),
                                    ),
                                )
                            },
                            downloadTotal = response.downloadTotal,
                            uploadTotal = response.uploadTotal,
                        )
                    }.onFailure { error ->
                        recordRuntimeError(
                            source = BackendRuntimeErrorSource.TRAFFIC,
                            prefix = "Failed to refresh connections",
                            error = error,
                        )
                    }
                    delay(3000)
                }
            }
        }
    }

    private fun observeMemory() {
        backendScope.launch {
            serviceState.collectLatest { state ->
                if (state != ServiceState.RUNNING) {
                    _memoryInfo.value = MemoryInfo(0, 0)
                    clearRuntimeError(BackendRuntimeErrorSource.MEMORY)
                    return@collectLatest
                }

                while (true) {
                    runCatching {
                        controller.getMemory()
                    }.onSuccess { response ->
                        clearRuntimeError(BackendRuntimeErrorSource.MEMORY)
                        _memoryInfo.value = MemoryInfo(
                            inUse = response.inuse,
                            osLimit = response.oslimit,
                        )
                    }.onFailure { error ->
                        recordRuntimeError(
                            source = BackendRuntimeErrorSource.MEMORY,
                            prefix = "Failed to refresh memory",
                            error = error,
                        )
                    }
                    delay(3000)
                }
            }
        }
    }

    private fun observeProxyGroups() {
        backendScope.launch {
            serviceState.collectLatest { state ->
                if (state != ServiceState.RUNNING) {
                    _proxyGroups.value = emptyList()
                    clearRuntimeError(BackendRuntimeErrorSource.PROXY_GROUPS)
                    return@collectLatest
                }

                while (true) {
                    runCatching {
                        fetchProxyGroupsFromController()
                    }.onSuccess { groups ->
                        clearRuntimeError(BackendRuntimeErrorSource.PROXY_GROUPS)
                        _proxyGroups.value = groups
                    }.onFailure { error ->
                        recordRuntimeError(
                            source = BackendRuntimeErrorSource.PROXY_GROUPS,
                            prefix = "Failed to refresh proxy groups",
                            error = error,
                        )
                    }
                    delay(3000)
                }
            }
        }
    }

    private fun refreshActiveProfile() {
        val savedPath = profilePrefs.getString(PROFILE_PATH_KEY, null)
        val profilesJson = profilePrefs.getString(PROFILES_LIST_KEY, null)

        if (profilesJson == null || savedPath == null) {
            _activeProfile.value = null
            return
        }

        val profile = runCatching {
            val jsonArray = JSONArray(profilesJson)
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                if (obj.getString("filePath") == savedPath || obj.getBoolean("isActive")) {
                    return@runCatching ProfileSummary(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        filePath = obj.getString("filePath"),
                        type = when (obj.optString("type", rs.chimera.android.model.ProfileType.LOCAL.name)) {
                            "REMOTE" -> ProfileType.REMOTE
                            else -> ProfileType.LOCAL
                        },
                        isActive = true,
                        isRemote = obj.optString("type", rs.chimera.android.model.ProfileType.LOCAL.name) == "REMOTE",
                        lastUpdated = obj.takeIf { it.has("lastUpdated") }?.getLong("lastUpdated"),
                        fileSize = obj.getLong("fileSize"),
                        url = obj.optString("url").takeIf { it.isNotBlank() },
                        autoUpdate = obj.optBoolean("autoUpdate", false),
                        userAgent = obj.optString("userAgent").takeIf { it.isNotBlank() },
                        proxyUrl = obj.optString("proxyUrl").takeIf { it.isNotBlank() },
                    )
                }
            }
            null as ProfileSummary?
        }.getOrNull()
        _activeProfile.value = profile
    }

    private fun appendProfile(profileJson: org.json.JSONObject) {
        val existingJson = profilePrefs.getString(PROFILES_LIST_KEY, null)
        val jsonArray = if (existingJson != null) JSONArray(existingJson) else JSONArray()

        val isFirst = jsonArray.length() == 0
        profileJson.put("isActive", isFirst)

        jsonArray.put(profileJson)
        profilePrefs.edit {
            putString(PROFILES_LIST_KEY, jsonArray.toString())
            if (isFirst) {
                putString(PROFILE_PATH_KEY, profileJson.getString("filePath"))
                Global.updateProfilePath(profileJson.getString("filePath"))
            }
        }

        if (isFirst) {
            refreshActiveProfile()
        }
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

    private fun profileStorageFileName(id: String, extension: String): String {
        val safeExtension = extension
            .replace(Regex("[^A-Za-z0-9]"), "")
            .lowercase(Locale.ROOT)
            .ifBlank { "yaml" }
        return "$id.$safeExtension"
    }

    private fun profileCatalogJson(): JSONArray {
        val value = profilePrefs.getString(PROFILES_LIST_KEY, null)
            ?: throw IllegalStateException("Profile catalog is empty")
        return JSONArray(value)
    }

    private fun validateRemoteProfileUrl(value: String) {
        val uri = runCatching { java.net.URI(value) }.getOrNull()
        require(uri?.host != null && uri.scheme in setOf("http", "https")) {
            "Remote profile URL must use http or https"
        }
    }

    private fun JSONArray.toCatalogEntries(): List<ProfileCatalogEntry> =
        (0 until length()).map { index ->
            val profile = getJSONObject(index)
            ProfileCatalogEntry(
                id = profile.getString("id"),
                name = profile.getString("name"),
                filePath = profile.getString("filePath"),
                isActive = profile.optBoolean("isActive", false),
            )
        }

    private suspend fun downloadProfileToAppDirectory(
        context: Context,
        profileId: String,
        request: RemoteProfileRequest,
    ): File {
        val remoteName = runCatching {
            java.net.URL(request.url).path.substringAfterLast('/').substringBefore('?')
        }.getOrNull().orEmpty()
        val extension = remoteName.substringAfterLast('.', "yaml")
        val file = File(context.filesDir, profileStorageFileName(profileId, extension))

        ChimeraFfi.ensureInitialized()
        val result = downloadFileWithProgress(
            url = request.url,
            outputPath = file.absolutePath,
            userAgent = request.userAgent,
            proxyUrl = request.proxyUrl ?: Global.proxyPort?.let { "http://127.0.0.1:$it" },
            progressCallback = object : DownloadProgressCallback {
                override fun onProgress(progress: DownloadProgress) {
                    // progress is intentionally not exposed through backend
                }
            },
        )

        if (!result.success) {
            throw IllegalStateException(result.errorMessage ?: "Unknown download error")
        }

        return file
    }

    private companion object {
        const val FILE_PREFS = "file_prefs"
        const val PROFILE_PATH_KEY = "profile_path"
        const val PROFILES_LIST_KEY = "profiles_list"
    }
}
