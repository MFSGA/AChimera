package rs.chimera.android.backend

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.Global
import rs.chimera.android.backend.model.ConnectionSnapshot
import rs.chimera.android.backend.model.ConnectionsSnapshot
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.backend.model.TrafficSnapshot
import rs.chimera.android.ffi.shutdownClash
import rs.chimera.android.service.TunService
import rs.chimera.android.service.tunService

class ChimeraBackendImpl : ChimeraBackend {
    private val mutableServiceState = MutableStateFlow(currentServiceState())
    private val mutableActiveProfile = MutableStateFlow<ProfileSummary?>(null)
    private val mutableTraffic = MutableStateFlow(TrafficSnapshot(0L, 0L, 0))

    override val serviceState: StateFlow<ServiceState> = mutableServiceState
    override val activeProfile: StateFlow<ProfileSummary?> = mutableActiveProfile
    override val traffic: StateFlow<TrafficSnapshot> = mutableTraffic

    override suspend fun prepareStartVpn(activity: Activity): StartVpnResult {
        if (Global.restoreProfilePath().isBlank()) {
            return StartVpnResult.Error("Please select a config file first")
        }

        val intent = VpnService.prepare(activity)
        return if (intent != null) {
            StartVpnResult.Prepared(intent)
        } else {
            startVpnAfterPermission()
            StartVpnResult.PermissionNotRequired
        }
    }

    override suspend fun startVpnAfterPermission() {
        val app = Global.application
        app.startService(Intent(app, TunService::class.java))
        mutableServiceState.value = ServiceState.STARTING
    }

    override suspend fun stopVpn() {
        shutdownClash()
        tunService?.stopVpn()
        mutableServiceState.value = currentServiceState()
    }

    override suspend fun listProfiles(): List<ProfileSummary> = emptyList()

    override suspend fun activateProfile(id: String) = Unit

    override suspend fun importLocalProfile(
        uri: Uri,
        name: String?,
    ) = Unit

    override suspend fun importRemoteProfile(request: RemoteProfileRequest) = Unit

    override suspend fun listProxyGroups(): List<ProxyGroupSnapshot> = emptyList()

    override suspend fun selectProxy(
        groupName: String,
        proxyName: String,
    ) = Unit

    override suspend fun testProxyDelay(proxyName: String): String = ""

    override suspend fun listConnections(): ConnectionsSnapshot {
        return ConnectionsSnapshot(
            connections = emptyList<ConnectionSnapshot>(),
            downloadTotal = 0L,
            uploadTotal = 0L,
        )
    }

    override suspend fun readRuntimeLogs(maxLines: Int): String {
        return Global.readRuntimeLogTail(maxLines)
    }

    override suspend fun clearRuntimeLogs() {
        Global.clearRuntimeLog()
    }

    override suspend fun updateSettings(patch: SettingsPatch) {
        val prefs = Global.application.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit {
            patch.allowLan?.let { putBoolean("allow_lan", it) }
            patch.mixedPort?.let { putString("mixed_port", it.toString()) }
            patch.httpPort?.let { putString("http_port", it.toString()) }
            patch.socksPort?.let { putString("socks_port", it.toString()) }
            patch.fakeIp?.let { putBoolean("fake_ip", it) }
            patch.ipv6?.let { putBoolean("ipv6", it) }
            patch.appFilterMode?.let { putString("app_filter_mode", it) }
            patch.allowedApps?.let { putStringSet("allowed_apps", it) }
            patch.disallowedApps?.let { putStringSet("disallowed_apps", it) }
        }
    }

    private fun currentServiceState(): ServiceState {
        return if (Global.isServiceRunning.value) {
            ServiceState.RUNNING
        } else {
            ServiceState.STOPPED
        }
    }
}
