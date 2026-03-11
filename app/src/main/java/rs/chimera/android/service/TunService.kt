package rs.chimera.android.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.AssetManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import rs.chimera.android.Global
import rs.chimera.android.ffi.ProfileOverride
import rs.chimera.android.ffi.initClash
import rs.chimera.android.ffi.shutdownClash
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

var tunService: TunService? = null

class TunService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunFd: Int? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isDestroying = false

    private data class ServiceSettings(
        val appFilterMode: String,
        val allowedApps: Set<String>,
        val disallowedApps: Set<String>,
        val allowLan: Boolean,
        val mixedPort: UShort,
        val httpPort: UShort?,
        val socksPort: UShort?,
        val fakeIp: Boolean,
        val ipv6: Boolean,
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        appendRuntimeLog("service onStartCommand")
        ensureForegroundService()
        tunService = this

        serviceScope.launch {
            try {
                runVpn()
            } catch (error: Exception) {
                Log.e(TAG, "Error in runVpn", error)
                appendRuntimeLog("service runVpn failed", error)
                Global.isServiceRunning.value = false
                cleanup()
                NotificationHelper.notifyFailed(this@TunService, error.message)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onRevoke() {
        cleanup()
        super.onRevoke()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }

    private suspend fun runVpn() {
        val profilePath = resolveProfilePath()
        val settings = loadServiceSettings()
        appendRuntimeLog("service preparing vpn for profile: $profilePath")
        val interfaceFd = buildTunnel(settings)
        if (interfaceFd == null) {
            error("Failed to establish VPN interface")
        }
        vpnInterface = interfaceFd
        tunFd = interfaceFd.fd

        copyRuntimeAssetsIfAvailable(Global.application.assets, Global.application.cacheDir)

        val currentTunFd = tunFd
        if (currentTunFd == null || currentTunFd <= 0) {
            error("Invalid tun fd: $currentTunFd")
        }

        val startResult = initClash(
            configPath = profilePath,
            workDir = Global.application.cacheDir.absolutePath,
            over = createProfileOverride(currentTunFd, settings),
        )
        if (startResult.isFailure) {
            throw startResult.exceptionOrNull()
                ?: IllegalStateException("Failed to initialize Rust core")
        }

        Global.proxyPort = settings.mixedPort
        appendRuntimeLog("service rust core started on mixed-port=${settings.mixedPort}")
        NotificationHelper.notifyRunning(this)
        Global.isServiceRunning.value = true
    }

    private fun buildTunnel(settings: ServiceSettings): ParcelFileDescriptor? {
        val builder = Builder()
        builder.setSession("ClashRS VPNService")
        builder.addAddress("10.0.0.1", 30)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("10.0.0.2")
        applyAppFilter(builder, settings)
        builder.allowBypass()
        return builder.establish()
    }

    private fun resolveProfilePath(): String {
        val path = if (Global.profilePath.isBlank()) {
            Global.restoreProfilePath()
        } else {
            Global.profilePath
        }.trim()

        if (path.isEmpty()) {
            throw IllegalStateException(getString(rs.chimera.android.R.string.service_profile_required))
        }

        val configFile = File(path)
        if (!configFile.exists() || !configFile.isFile) {
            throw IllegalStateException("Profile file not found: $path")
        }

        return path
    }

    private fun loadServiceSettings(): ServiceSettings {
        val prefs = Global.application.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return ServiceSettings(
            appFilterMode = prefs.getString("app_filter_mode", "ALL") ?: "ALL",
            allowedApps = prefs.getStringSet("allowed_apps", emptySet()) ?: emptySet(),
            disallowedApps = prefs.getStringSet("disallowed_apps", emptySet()) ?: emptySet(),
            allowLan = prefs.getBoolean("allow_lan", false),
            mixedPort = prefs.getPort("mixed_port", 7890u),
            httpPort = prefs.getOptionalPort("http_port"),
            socksPort = prefs.getOptionalPort("socks_port"),
            fakeIp = prefs.getBoolean("fake_ip", false),
            ipv6 = prefs.getBoolean("ipv6", true),
        )
    }

    private fun createProfileOverride(
        currentTunFd: Int,
        settings: ServiceSettings,
    ): ProfileOverride {
        return ProfileOverride(
            tunFd = currentTunFd,
            logFilePath = "${Global.application.cacheDir}/chimera-rs.log",
            allowLan = settings.allowLan,
            mixedPort = settings.mixedPort,
            httpPort = settings.httpPort,
            socksPort = settings.socksPort,
            fakeIp = settings.fakeIp,
            ipv6 = settings.ipv6,
        )
    }

    private fun applyAppFilter(
        builder: Builder,
        settings: ServiceSettings,
    ) {
        when (settings.appFilterMode) {
            "ALLOWED" -> {
                settings.allowedApps.forEach { appPackageName ->
                    runCatching { builder.addAllowedApplication(appPackageName) }
                        .onFailure { error ->
                            Log.w(TAG, "Failed to add allowed app: $appPackageName", error)
                        }
                }
            }
            "DISALLOWED" -> {
                addDisallowedApplicationSafely(builder, packageName)
                settings.disallowedApps.forEach { appPackageName ->
                    addDisallowedApplicationSafely(builder, appPackageName)
                }
            }
            else -> addDisallowedApplicationSafely(builder, packageName)
        }
    }

    private fun addDisallowedApplicationSafely(
        builder: Builder,
        appPackageName: String,
    ) {
        runCatching { builder.addDisallowedApplication(appPackageName) }
            .onFailure { error ->
                Log.w(TAG, "Failed to add disallowed app: $appPackageName", error)
                appendRuntimeLog("failed to add disallowed app: $appPackageName", error)
            }
    }

    private fun copyRuntimeAssetsIfAvailable(assets: AssetManager, cacheDir: File) {
        listOf("Country.mmdb", "geosite.dat").forEach { name ->
            runCatching {
                assets.open("clash-res/$name").use { input ->
                    val output = File(cacheDir, name)
                    output.deleteOnExit()
                    if (!output.exists()) {
                        output.createNewFile()
                    }
                    output.outputStream().use { stream ->
                        input.copyTo(stream)
                    }
                }
            }.onFailure { error ->
                Log.w(TAG, "Runtime asset unavailable: $name", error)
                appendRuntimeLog("runtime asset unavailable: $name", error)
            }
        }
    }

    private fun ensureForegroundService() {
        NotificationHelper.ensureChannel(this)
        val notification = NotificationHelper.buildStartingNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun cleanup() {
        synchronized(this) {
            if (isDestroying) {
                return
            }
            isDestroying = true
        }

        shutdownClash().exceptionOrNull()?.let { error ->
            Log.w(TAG, "Failed to stop Rust core cleanly", error)
            appendRuntimeLog("failed to stop rust core cleanly", error)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to stop foreground service", error)
            appendRuntimeLog("failed to stop foreground service", error)
        }
        runCatching { vpnInterface?.close() }
            .onFailure { error ->
                Log.w(TAG, "Failed to close VPN interface", error)
                appendRuntimeLog("failed to close vpn interface", error)
            }
        vpnInterface = null
        tunFd = null
        tunService = null
        Global.proxyPort = null
        Global.isServiceRunning.value = false
        appendRuntimeLog("service cleanup complete")
    }

    fun stopVpn() {
        cleanup()
        stopSelf()
    }

    private companion object {
        const val TAG = "ChimeraTunService"
    }

    private fun appendRuntimeLog(
        message: String,
        error: Throwable? = null,
    ) {
        val file = Global.runtimeLogFile()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line = buildString {
            append('[')
            append(timestamp)
            append("] ")
            append(message)
            if (error != null) {
                append(": ")
                append(error.message ?: error.javaClass.simpleName)
            }
        }

        runCatching {
            file.parentFile?.mkdirs()
            file.appendText("$line\n")
        }
    }
}

private fun SharedPreferences.getOptionalPort(key: String): UShort? {
    val value = all[key] ?: return null
    val intValue = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    } ?: return null

    return intValue.toUShort()
}

private fun SharedPreferences.getPort(
    key: String,
    defaultValue: UShort,
): UShort = getOptionalPort(key) ?: defaultValue
