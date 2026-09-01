package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProxyMode

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
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
    private val proxyProviderOperations = ProxyProviderOperationCoordinator()

    suspend fun fetchTraffic(): TrafficSnapshot = withControllerContext {
        controller.getConnectionSummary().let { summary ->
            TrafficSnapshot(
                downloadTotal = summary.downloadTotal,
                uploadTotal = summary.uploadTotal,
                connectionCount = summary.connectionCount,
            )
        }
    }

    suspend fun fetchMemory(): MemoryInfo = withControllerContext {
        controller.getMemory().let { response ->
            MemoryInfo(
                inUse = response.inuse,
                osLimit = response.oslimit,
            )
        }
    }

    suspend fun fetchProxyGroups(): List<ProxyGroupSnapshot> = withControllerContext {
        val mode = controller.getMode() ?: Mode.RULE
        controller.getProxies().toProxyGroupSnapshots(mode)
    }

    suspend fun listProxyGroups(): List<ProxyGroupSnapshot> =
        runProxyOperation("Failed to refresh proxy groups", ::fetchProxyGroups)

    suspend fun selectProxy(groupName: String, proxyName: String) {
        runProxyOperation("Failed to select proxy") {
            controller.selectProxy(groupName, proxyName)
        }
    }

    suspend fun setMode(mode: ProxyMode) {
        runProxyOperation("Failed to switch proxy mode") {
            controller.setMode(mode.toNativeMode())
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

    suspend fun listRules(): List<RuleSnapshot> = withControllerContext {
        requireProxyServiceRunning()
        controller.getRules().map { rule ->
            RuleSnapshot(
                type = rule.ruleType,
                proxy = rule.proxy,
                payload = rule.payload,
            )
        }
    }

    suspend fun listProxyProviders(): List<ProxyProviderSnapshot> = withControllerContext {
        requireProxyServiceRunning()
        controller.getProxyProviders().map { provider ->
            ProxyProviderSnapshot(
                name = provider.name,
                type = provider.providerType,
                vehicleType = provider.vehicleType,
                proxyCount = provider.proxyCount,
            )
        }
    }

    suspend fun updateProxyProvider(name: String) = proxyProviderOperations.withLock(name) {
        runProxyOperation("Failed to update proxy provider") {
            controller.updateProxyProvider(name)
        }
    }

    suspend fun healthcheckProxyProvider(name: String) = proxyProviderOperations.withLock(name) {
        runProxyOperation("Failed to healthcheck proxy provider") {
            controller.healthcheckProxyProvider(name)
        }
    }

    suspend fun queryDns(name: String, recordType: String): String = withControllerContext {
        requireProxyServiceRunning()
        controller.queryDns(name, recordType)
    }

    private fun requireProxyServiceRunning() {
        RuntimeOperationPolicy.requireRunning(serviceState.value, notRunningMessage)
    }

    private suspend fun <T> runProxyOperation(
        errorPrefix: String,
        operation: suspend () -> T,
    ): T = withControllerContext {
        requireProxyServiceRunning()
        try {
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
    ): T = withControllerContext {
        requireProxyServiceRunning()
        try {
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

    private suspend fun <T> withControllerContext(operation: suspend () -> T): T =
        withContext(Dispatchers.IO) { operation() }
}
