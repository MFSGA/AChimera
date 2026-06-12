package rs.chimera.android.backend

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.backend.model.*

interface ChimeraBackend {
    val serviceState: StateFlow<ServiceState>
    val activeProfile: StateFlow<ProfileSummary?>
    val traffic: StateFlow<TrafficSnapshot>

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
    suspend fun testProxyDelay(proxyName: String): String
    suspend fun listConnections(): ConnectionsSnapshot
    suspend fun readRuntimeLogs(maxLines: Int = 160): String
    suspend fun clearRuntimeLogs()
    suspend fun updateSettings(patch: SettingsPatch)
}