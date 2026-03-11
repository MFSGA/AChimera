package rs.chimera.android.service

import android.content.Context
import android.content.Intent
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

var tunService: TunService? = null

class TunService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunFd: Int? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isDestroying = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        ensureForegroundService()
        tunService = this

        serviceScope.launch {
            try {
                runVpn()
            } catch (error: Exception) {
                Log.e(TAG, "Error in runVpn", error)
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
        val prefs = Global.application.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val interfaceFd = buildTunnel()
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
            configPath = Global.profilePath,
            workDir = Global.application.cacheDir.absolutePath,
            over = ProfileOverride(
                tunFd = currentTunFd,
                logFilePath = "${Global.application.cacheDir}/chimera-rs.log",
                fakeIp = prefs.getBoolean("fake_ip", false),
                ipv6 = prefs.getBoolean("ipv6", true),
            ),
        )
        if (startResult.isFailure) {
            throw startResult.exceptionOrNull()
                ?: IllegalStateException("Failed to initialize Rust core")
        }

        NotificationHelper.notifyRunning(this)
        Global.isServiceRunning.value = true
    }

    private fun buildTunnel(): ParcelFileDescriptor? {
        val builder = Builder()
        builder.setSession("ClashRS VPNService")
        builder.addAddress("10.0.0.1", 30)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("10.0.0.2")
        builder.addDisallowedApplication(packageName)
        builder.allowBypass()
        return builder.establish()
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
        }
        runCatching { vpnInterface?.close() }
            .onFailure { error -> Log.w(TAG, "Failed to close VPN interface", error) }
        vpnInterface = null
        tunFd = null
        tunService = null
        Global.isServiceRunning.value = false
    }

    fun stopVpn() {
        cleanup()
        stopSelf()
    }

    private companion object {
        const val TAG = "ChimeraTunService"
    }
}
