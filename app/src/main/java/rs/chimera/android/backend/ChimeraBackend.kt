package rs.chimera.android.backend

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.ConnectionsSnapshot
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.backend.model.TrafficSnapshot

interface ChimeraBackend {
    val serviceState: StateFlow<ServiceState>
    val activeProfile: StateFlow<ProfileSummary?>
    val traffic: StateFlow<TrafficSnapshot>
    val memoryInfo: StateFlow<MemoryInfo>
    val proxyGroups: StateFlow<List<ProxyGroupSnapshot>>
    val connections: StateFlow<ConnectionsSnapshot>
    val runtimeError: StateFlow<BackendRuntimeError?>

    suspend fun prepareStartVpn(context: Context): StartVpnResult
    suspend fun startVpnAfterPermission()
    suspend fun stopVpn()

    suspend fun listProfiles(): List<ProfileSummary>
    suspend fun activateProfile(id: String)
    suspend fun deleteProfile(id: String)
    suspend fun renameProfile(id: String, newName: String)
    suspend fun importLocalProfile(uri: Uri, name: String?)
    suspend fun importRemoteProfile(request: RemoteProfileRequest)
    suspend fun updateRemoteProfile(id: String)
    suspend fun verifyProfile(filePath: String): Result<String>
    suspend fun listProxyGroups(): List<ProxyGroupSnapshot>
    suspend fun selectProxy(groupName: String, proxyName: String)
    suspend fun setMode(mode: uniffi.chimera_ffi.Mode)
    suspend fun testProxyDelay(proxyName: String): String
    suspend fun listConnections(): ConnectionsSnapshot
    suspend fun readRuntimeLogs(maxLines: Int = 160): String
    suspend fun clearRuntimeLogs()
    suspend fun updateSettings(patch: SettingsPatch)
}
