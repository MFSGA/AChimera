package rs.chimera.android.backend

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.provider.OpenableColumns
import androidx.core.content.edit
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
import rs.chimera.android.backend.model.*
import rs.chimera.android.ffi.ChimeraFfi
import rs.chimera.android.ffi.initClash
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

    init {
        refreshActiveProfile()
        observeTraffic()
    }

    override suspend fun prepareStartVpn(context: Context): StartVpnResult {
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
            Global.application.startService(Intent(Global.application, TunService::class.java))
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

        return runCatching {
            val jsonArray = JSONArray(profilesJson)
            buildList {
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
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun activateProfile(id: String) {
        val profilesJson = profilePrefs.getString(PROFILES_LIST_KEY, null) ?: return

        runCatching {
            val jsonArray = JSONArray(profilesJson)
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                obj.put("isActive", obj.getString("id") == id)
            }
            profilePrefs.edit {
                putString(PROFILES_LIST_KEY, jsonArray.toString())
                val activePath = (0 until jsonArray.length())
                    .map { jsonArray.getJSONObject(it) }
                    .firstOrNull { it.getString("id") == id }
                    ?.getString("filePath")
                putString(PROFILE_PATH_KEY, activePath)
            }
            val path = (0 until jsonArray.length())
                .map { jsonArray.getJSONObject(it) }
                .firstOrNull { it.getString("id") == id }
                ?.getString("filePath")
            if (path != null) {
                Global.updateProfilePath(path)
            }
        }
        refreshActiveProfile()
    }

    override suspend fun importLocalProfile(uri: Uri, name: String?) {
        val context = Global.application
        val fileName = queryDisplayName(context, uri)
        val safeName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.')
        val extension = fileName.substringAfterLast('.', "yaml")
        val safeFileName = sanitizeFileName("$safeName.$extension")
        val file = File(context.filesDir, safeFileName)

        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        val profileJson = org.json.JSONObject()
        val id = UUID.randomUUID().toString()
        profileJson.put("id", id)
        profileJson.put("name", file.name)
        profileJson.put("filePath", file.absolutePath)
        profileJson.put("createdAt", System.currentTimeMillis())
        profileJson.put("isActive", false)
        profileJson.put("fileSize", file.length())
        profileJson.put("type", rs.chimera.android.model.ProfileType.LOCAL.name)

        appendProfile(profileJson)
    }

    override suspend fun importRemoteProfile(request: RemoteProfileRequest) {
        val context = Global.application
        val resolvedName = request.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(Date())

        val file = withContext(Dispatchers.IO) {
            downloadProfileToAppDirectory(context, resolvedName, request)
        }

        val profileJson = org.json.JSONObject()
        val id = UUID.randomUUID().toString()
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
        val profilesJson = profilePrefs.getString(PROFILES_LIST_KEY, null) ?: return

        runCatching {
            val jsonArray = JSONArray(profilesJson)
            val updatedArray = JSONArray()
            var wasActive = false
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                if (obj.getString("id") == id) {
                    wasActive = obj.getBoolean("isActive")
                    File(obj.getString("filePath")).takeIf { it.exists() }?.delete()
                } else {
                    updatedArray.put(obj)
                }
            }
            if (updatedArray.length() > 0 && wasActive) {
                updatedArray.getJSONObject(0).put("isActive", true)
            }
            profilePrefs.edit {
                putString(PROFILES_LIST_KEY, updatedArray.toString())
                val activePath = if (updatedArray.length() > 0) {
                    updatedArray.getJSONObject(0).getString("filePath")
                } else null
                putString(PROFILE_PATH_KEY, activePath)
            }
            val firstPath = if (updatedArray.length() > 0) {
                updatedArray.getJSONObject(0).getString("filePath")
            } else null
            Global.updateProfilePath(firstPath.orEmpty())
        }
        refreshActiveProfile()
    }

    override suspend fun renameProfile(id: String, newName: String) {
        val profilesJson = profilePrefs.getString(PROFILES_LIST_KEY, null) ?: return

        runCatching {
            val jsonArray = JSONArray(profilesJson)
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                if (obj.getString("id") == id) {
                    obj.put("name", newName)
                }
            }
            profilePrefs.edit {
                putString(PROFILES_LIST_KEY, jsonArray.toString())
            }
        }
        refreshActiveProfile()
    }

    override suspend fun updateRemoteProfile(id: String) {
        val profilesJson = profilePrefs.getString(PROFILES_LIST_KEY, null) ?: return

        val targetProfile = runCatching {
            val jsonArray = JSONArray(profilesJson)
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                if (obj.getString("id") == id) {
                    return@runCatching obj
                }
            }
            null
        }.getOrNull() ?: return

        if (targetProfile.optString("type", "LOCAL") != "REMOTE") return
        val url = targetProfile.optString("url").takeIf { it.isNotBlank() } ?: return

        val context = Global.application
        val profileName = targetProfile.getString("name")
        val userAgent = targetProfile.optString("userAgent").takeIf { it.isNotBlank() }
        val proxyUrl = targetProfile.optString("proxyUrl").takeIf { it.isNotBlank() }
            ?: Global.proxyPort?.let { "http://127.0.0.1:$it" }

        val file = withContext(Dispatchers.IO) {
            val remoteName = runCatching {
                java.net.URL(url).path.substringAfterLast('/').substringBefore('?')
            }.getOrNull().orEmpty()
            val extension = remoteName.substringAfterLast('.', "yaml")
            val fileName = sanitizeFileName("$profileName.$extension")
            val outputFile = File(context.filesDir, fileName)

            ChimeraFfi.ensureInitialized()
            val result = downloadFileWithProgress(
                url = url,
                outputPath = outputFile.absolutePath,
                userAgent = userAgent,
                proxyUrl = proxyUrl,
                progressCallback = object : DownloadProgressCallback {
                    override fun onProgress(progress: DownloadProgress) {}
                },
            )
            if (!result.success) {
                throw IllegalStateException(result.errorMessage ?: "Unknown download error")
            }
            outputFile
        }

        runCatching {
            val jsonArray = JSONArray(profilesJson)
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
                val activePath = (0 until jsonArray.length())
                    .map { jsonArray.getJSONObject(it) }
                    .firstOrNull { it.getBoolean("isActive") }
                    ?.getString("filePath")
                if (activePath != null) putString(PROFILE_PATH_KEY, activePath)
                val activeProfile = (0 until jsonArray.length())
                    .map { jsonArray.getJSONObject(it) }
                    .firstOrNull { it.getBoolean("isActive") }
                if (activeProfile?.getString("id") == id) {
                    Global.updateProfilePath(file.absolutePath)
                }
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

    override suspend fun listProxyGroups(): List<ProxyGroupSnapshot> {
        if (serviceState.value != ServiceState.RUNNING) return emptyList()

        return runCatching {
            val proxies = controller.getProxies()
            val proxyMap = mutableMapOf<String, ProxySnapshot>()

            proxies.forEach { proxy ->
                proxyMap[proxy.name] = ProxySnapshot(
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

            proxies.map { proxy ->
                ProxyGroupSnapshot(
                    name = proxy.name,
                    proxies = proxy.all,
                    selected = proxy.now,
                    mode = controller.getMode() ?: Mode.RULE,
                    proxyDetails = proxyMap,
                )
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun selectProxy(groupName: String, proxyName: String) {
        runCatching {
            controller.selectProxy(groupName, proxyName)
        }
    }

    override suspend fun testProxyDelay(proxyName: String): String {
        return runCatching {
            val response = controller.getProxyDelay(proxyName, null, null)
            "${response.delay}ms"
        }.getOrDefault("timeout")
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
            patch.httpPort?.let { putInt("http_port", it.toInt()) }
            patch.socksPort?.let { putInt("socks_port", it.toInt()) }
            patch.fakeIp?.let { putBoolean("fake_ip", it) }
            patch.ipv6?.let { putBoolean("ipv6", it) }
            patch.appFilterMode?.let { putString("app_filter_mode", it) }
            patch.allowedApps?.let { putStringSet("allowed_apps", it) }
            patch.disallowedApps?.let { putStringSet("disallowed_apps", it) }
        }
    }

    private fun observeTraffic() {
        backendScope.launch {
            serviceState.collectLatest { state ->
                if (state != ServiceState.RUNNING) {
                    _traffic.value = TrafficSnapshot(0, 0, 0)
                    return@collectLatest
                }

                delay(1000)
                while (true) {
                    runCatching {
                        controller.getConnections()
                    }.onSuccess { response ->
                        _traffic.value = TrafficSnapshot(
                            downloadTotal = response.downloadTotal,
                            uploadTotal = response.uploadTotal,
                            connectionCount = response.connections.size,
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

    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "remote-profile.yaml" }
    }

    private suspend fun downloadProfileToAppDirectory(
        context: Context,
        profileName: String,
        request: RemoteProfileRequest,
    ): File {
        val remoteName = runCatching {
            java.net.URL(request.url).path.substringAfterLast('/').substringBefore('?')
        }.getOrNull().orEmpty()
        val extension = remoteName.substringAfterLast('.', "yaml")
        val fileName = sanitizeFileName("$profileName.$extension")
        val file = File(context.filesDir, fileName)

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
