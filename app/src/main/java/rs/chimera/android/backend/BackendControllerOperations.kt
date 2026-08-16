package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ConnectionsSnapshot
import rs.chimera.android.backend.model.MemoryInfo
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyProviderSnapshot
import rs.chimera.android.backend.model.RuleSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.TrafficSnapshot
import uniffi.chimera_ffi.ClashController
import uniffi.chimera_ffi.Mode

internal class BackendControllerOperations(
    socketPath: String,
    private val serviceState: StateFlow<ServiceState>,
    private val notRunningMessage: () -> String,
    private val recordRuntimeError: (BackendRuntimeErrorSource, String, Throwable) -> Unit,
    private val clearRuntimeError: (BackendRuntimeErrorSource) -> Unit,
) {
    private val controller by lazy { ClashController(socketPath) }

    suspend fun fetchTraffic(): TrafficSnapshot =
        controller.getConnectionSummary().let { summary ->
            TrafficSnapshot(
                downloadTotal = summary.downloadTotal,
                uploadTotal = summary.uploadTotal,
                connectionCount = summary.connectionCount,
            )
        }

    suspend fun fetchMemory(): MemoryInfo =
        controller.getMemory().let { response ->
            MemoryInfo(
                inUse = response.inuse,
                osLimit = response.oslimit,
            )
        }

    suspend fun fetchProxyGroups(): List<ProxyGroupSnapshot> {
        val mode = controller.getMode() ?: Mode.RULE
        return controller.getProxies().toProxyGroupSnapshots(mode)
    }

    suspend fun listProxyGroups(): List<ProxyGroupSnapshot> =
        runProxyOperation("Failed to refresh proxy groups", ::fetchProxyGroups)

    suspend fun selectProxy(groupName: String, proxyName: String) {
        runProxyOperation("Failed to select proxy") {
            controller.selectProxy(groupName, proxyName)
        }
    }

    suspend fun setMode(mode: Mode) {
        runProxyOperation("Failed to switch proxy mode") {
            controller.setMode(mode)
        }
    }

    suspend fun resetNetwork() {
        runProxyOperation("Failed to reset network state") {
            controller.resetNetwork()
        }
    }

    suspend fun testProxyDelay(proxyName: String): String =
        runProxyOperation("Failed to test proxy delay") {
            val response = controller.getProxyDelay(proxyName, null, null)
            "${response.delay}ms"
        }

    suspend fun listConnections(): ConnectionsSnapshot =
        runConnectionOperation("Failed to refresh connections") {
            controller.getConnections().toConnectionsSnapshot()
        }

    suspend fun closeConnection(id: String) {
        runConnectionOperation("Failed to close connection") {
            controller.closeConnection(id)
        }
    }

    suspend fun closeAllConnections() {
        runConnectionOperation("Failed to close all connections") {
            controller.closeAllConnections()
        }
    }

    suspend fun listRules(): List<RuleSnapshot> {
        requireProxyServiceRunning()
        return controller.getRules().map { rule ->
            RuleSnapshot(
                type = rule.ruleType,
                proxy = rule.proxy,
                payload = rule.payload,
            )
        }
    }

    suspend fun listProxyProviders(): List<ProxyProviderSnapshot> {
        requireProxyServiceRunning()
        return controller.getProxyProviders().map { provider ->
            ProxyProviderSnapshot(
                name = provider.name,
                type = provider.providerType,
                vehicleType = provider.vehicleType,
                proxyCount = provider.proxyCount,
            )
        }
    }

    suspend fun updateProxyProvider(name: String) {
        requireProxyServiceRunning()
        controller.updateProxyProvider(name)
    }

    suspend fun healthcheckProxyProvider(name: String) {
        requireProxyServiceRunning()
        controller.healthcheckProxyProvider(name)
    }

    suspend fun queryDns(name: String, recordType: String): String {
        requireProxyServiceRunning()
        return controller.queryDns(name, recordType)
    }

    private fun requireProxyServiceRunning() {
        RuntimeOperationPolicy.requireRunning(serviceState.value, notRunningMessage)
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
                BackendRuntimeErrorSource.PROXY_GROUPS,
                errorPrefix,
                error,
            )
            throw error
        }
    }

    private suspend fun <T> runConnectionOperation(
        errorPrefix: String,
        operation: suspend () -> T,
    ): T {
        requireProxyServiceRunning()
        return try {
            operation().also { clearRuntimeError(BackendRuntimeErrorSource.TRAFFIC) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recordRuntimeError(
                BackendRuntimeErrorSource.TRAFFIC,
                errorPrefix,
                error,
            )
            throw error
        }
    }
}
