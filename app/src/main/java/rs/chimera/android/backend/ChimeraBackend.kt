package rs.chimera.android.backend

import rs.chimera.android.backend.model.VpnRuntimeStatus

import rs.chimera.android.backend.model.ProxyMode

import rs.chimera.android.settings.RuntimeSettings

import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.ConnectionsSnapshot
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyProviderSnapshot
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.backend.model.RemoteProfileSettings
import rs.chimera.android.backend.model.RuleSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.SettingsApplyEffect
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.backend.model.TrafficSnapshot
import rs.chimera.android.backend.model.VpnSystemStatus
import rs.chimera.android.backend.model.ProfileDownloadProgress

interface ChimeraBackend {
    suspend fun awaitReady()

    val settings: StateFlow<RuntimeSettings>
    val runtimeStatus: StateFlow<VpnRuntimeStatus>
    val serviceState: StateFlow<ServiceState>
    val serviceError: StateFlow<String?>
    val vpnSystemStatus: StateFlow<VpnSystemStatus>
    val activeProfile: StateFlow<ProfileSummary?>
    val traffic: StateFlow<TrafficSnapshot>
    val memoryInfo: StateFlow<MemoryInfo>
    val proxyGroups: StateFlow<List<ProxyGroupSnapshot>>
    val runtimeError: StateFlow<BackendRuntimeError?>

    suspend fun prepareStartVpn(): StartVpnResult
    suspend fun startVpnAfterPermission()
    suspend fun stopVpn()
    suspend fun restartVpn()

    suspend fun listProfiles(): List<ProfileSummary>
    suspend fun activateProfile(id: String)
    suspend fun deleteProfile(id: String)
    suspend fun renameProfile(id: String, newName: String)
    suspend fun importLocalProfile(uri: Uri, name: String?): String
    suspend fun importRemoteProfile(
        request: RemoteProfileRequest,
        onProgress: (ProfileDownloadProgress) -> Unit = {},
    ): String
    suspend fun updateRemoteProfile(
        id: String,
        onProgress: (ProfileDownloadProgress) -> Unit = {},
    )
    suspend fun updateRemoteProfileSettings(id: String, settings: RemoteProfileSettings)
    suspend fun verifyProfile(filePath: String): Result<String>
    suspend fun listProxyGroups(): List<ProxyGroupSnapshot>
    suspend fun selectProxy(groupName: String, proxyName: String)
    suspend fun setMode(mode: ProxyMode)
    suspend fun resetNetwork()
    suspend fun testProxyDelay(proxyName: String): String
    suspend fun listConnections(): ConnectionsSnapshot
    suspend fun closeConnection(id: String)
    suspend fun closeAllConnections()
    suspend fun listRules(): List<RuleSnapshot>
    suspend fun listProxyProviders(): List<ProxyProviderSnapshot>
    suspend fun updateProxyProvider(name: String)
    suspend fun healthcheckProxyProvider(name: String)
    suspend fun queryDns(name: String, recordType: String): String
    suspend fun readRuntimeLogs(maxLines: Int = 160): String
    suspend fun clearRuntimeLogs()
    suspend fun buildDiagnosticsBundle(): String
    suspend fun updateSettings(patch: SettingsPatch): SettingsApplyEffect
}
