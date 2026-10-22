package rs.chimera.android.ui.metacubex.design

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ProviderSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.VpnRuntimeStatus

class ProviderDisplayPolicyTest {
    @Test
    fun allProviderVehicleTypesRemainVisibleInCmfaOrder() {
        val providers = listOf(
            provider(name = "inline", vehicleType = "Inline", type = "Proxy"),
            provider(name = "compatible-rule", vehicleType = "Compatible", type = "Rule"),
            provider(name = "file", vehicleType = "File", type = "Proxy"),
            provider(name = "compatible-proxy", vehicleType = "Compatible", type = "Proxy"),
            provider(name = "http", vehicleType = "HTTP", type = "Proxy"),
        )

        assertEquals(
            listOf("compatible-proxy", "file", "http", "inline", "compatible-rule"),
            ProviderDisplayPolicy.sorted(providers).map(ProviderSnapshot::name),
        )
    }

    @Test
    fun runtimeTransitionsCancelInFlightProviderLoadAndReloadOnRecovery() = runBlocking {
        val runtimeStatus = MutableStateFlow(VpnRuntimeStatus(ServiceState.RUNNING))
        val states = mutableListOf<ServiceState>()
        val firstLoadStarted = CompletableDeferred<Unit>()
        val firstLoadCancelled = CompletableDeferred<Unit>()
        val stoppingReceived = CompletableDeferred<Unit>()
        val startingReceived = CompletableDeferred<Unit>()
        val recoveredLoadStarted = CompletableDeferred<Unit>()
        var loadCount = 0

        val collector = launch {
            runtimeStatus
                .providerRuntimeStates()
                .onEach { state ->
                    states += state
                    when (state) {
                        ServiceState.STOPPING -> stoppingReceived.complete(Unit)
                        ServiceState.STARTING -> startingReceived.complete(Unit)
                        else -> Unit
                    }
                }.collectLatest { state ->
                    if (state == ServiceState.RUNNING) {
                        if (loadCount++ == 0) {
                            firstLoadStarted.complete(Unit)
                            try {
                                awaitCancellation()
                            } finally {
                                firstLoadCancelled.complete(Unit)
                            }
                        } else {
                            recoveredLoadStarted.complete(Unit)
                            awaitCancellation()
                        }
                    }
                }
            }

        firstLoadStarted.await()
        runtimeStatus.value = VpnRuntimeStatus(ServiceState.STOPPING)
        stoppingReceived.await()
        firstLoadCancelled.await()
        runtimeStatus.value = VpnRuntimeStatus(ServiceState.STARTING)
        startingReceived.await()
        runtimeStatus.value = VpnRuntimeStatus(ServiceState.RUNNING)
        recoveredLoadStarted.await()
        collector.cancelAndJoin()

        assertEquals(
            listOf(ServiceState.RUNNING, ServiceState.STOPPING, ServiceState.STARTING, ServiceState.RUNNING),
            states,
        )
        assertEquals(2, loadCount)
    }

    private fun provider(
        vehicleType: String,
        type: String = "Proxy",
        name: String = "test",
    ) = ProviderSnapshot(
        name = name,
        type = type,
        vehicleType = vehicleType,
        updatedAtMillis = null,
    )
}
