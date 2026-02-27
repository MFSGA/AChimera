package rs.chimera.android.service

import android.content.Intent
import android.content.res.AssetManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand")
        CoroutineScope(Dispatchers.Default).launch {
            runVpn()
        }

        tunService = this
        Global.isServiceRunning.value = true
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private suspend fun runVpn() {
        val interfaceFd = buildTunnel()
        if (interfaceFd == null) {
            Log.e(TAG, "Failed to establish VPN interface")
            stopVpn()
            return
        }
        vpnInterface = interfaceFd
        tunFd = interfaceFd.fd

        copyRuntimeAssetsIfAvailable(Global.application.assets, Global.application.cacheDir)

        val currentTunFd = tunFd
        if (currentTunFd == null || currentTunFd <= 0) {
            Log.e(TAG, "Invalid tun fd: $currentTunFd")
            stopVpn()
            return
        }

        val startResult = initClash(
            configPath = Global.profilePath,
            workDir = Global.application.cacheDir.absolutePath,
            over = ProfileOverride(
                tunFd = currentTunFd,
                logFilePath = "${Global.application.cacheDir}/chimera-rs.log",
            ),
        )
        if (startResult.isFailure) {
            Log.e(TAG, "Failed to initialize Rust core", startResult.exceptionOrNull())
            stopVpn()
        }
    }

    private fun buildTunnel(): ParcelFileDescriptor? {
        val builder = Builder()
        builder.setSession("ClashRS VPNService")
        builder.addAddress("10.0.0.1", 30)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer("10.0.0.2")
        builder.addDisallowedApplication(packageName)
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

    fun stopVpn() {
        shutdownClash().exceptionOrNull()?.let { error ->
            Log.w(TAG, "Failed to stop Rust core cleanly", error)
        }
        vpnInterface?.close()
        vpnInterface = null
        tunFd = null
        stopSelf()
        tunService = null
        Global.isServiceRunning.value = false
    }

    private companion object {
        const val TAG = "ChimeraTunService"
    }
}
