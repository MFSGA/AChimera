package rs.chimera.android.backend

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import rs.chimera.android.Global
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.TrafficSnapshot
import rs.chimera.android.backend.model.VpnSystemStatus
import rs.chimera.android.service.RuntimeLogSanitizer

internal class BackendDiagnosticsOperations(
    private val context: Context,
    private val serviceState: StateFlow<ServiceState>,
    private val serviceError: StateFlow<String?>,
    private val vpnSystemStatus: StateFlow<VpnSystemStatus>,
    private val activeProfile: StateFlow<ProfileSummary?>,
    private val traffic: StateFlow<TrafficSnapshot>,
    private val memoryInfo: StateFlow<MemoryInfo>,
    private val proxyGroups: StateFlow<List<ProxyGroupSnapshot>>,
    private val runtimeError: StateFlow<BackendRuntimeError?>,
) {
    suspend fun readRuntimeLogs(maxLines: Int): String =
        withContext(Dispatchers.IO) {
            RuntimeLogSanitizer.sanitizePrivatePaths(
                value = Global.readRuntimeLogTail(maxLines),
                privatePathPrefixes = listOf(context.applicationInfo.dataDir),
            )
        }

    suspend fun clearRuntimeLogs() {
        withContext(Dispatchers.IO) {
            Global.clearRuntimeLog()
        }
    }

    suspend fun buildDiagnosticsBundle(): String = withContext(Dispatchers.IO) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        DiagnosticsBundleBuilder.build(
            DiagnosticsBundleInput(
                generatedAtEpochMillis = System.currentTimeMillis(),
                appVersion = packageInfo.versionName.orEmpty(),
                appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                androidSdk = Build.VERSION.SDK_INT,
                serviceState = serviceState.value,
                serviceError = serviceError.value,
                vpnSystemStatus = vpnSystemStatus.value,
                activeProfile = activeProfile.value,
                traffic = traffic.value,
                memoryInfo = memoryInfo.value,
                proxyGroupCount = proxyGroups.value.size,
                runtimeError = runtimeError.value,
                runtimeLogs = Global.readRuntimeLogTail(500),
                privatePathPrefixes = listOf(context.applicationInfo.dataDir),
            ),
        )
    }
}
