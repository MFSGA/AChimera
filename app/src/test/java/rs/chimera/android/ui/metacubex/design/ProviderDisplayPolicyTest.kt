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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProviderSnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.VpnRuntimeStatus

class ProviderDisplayPolicyTest {
    @Test
    fun compatibleProvidersAreExcludedLikeCmfa() {
        assertFalse(provider(vehicleType = "Compatible").let(ProviderDisplayPolicy::isVisible))
        assertFalse(provider(vehicleType = "compatible").let(ProviderDisplayPolicy::isVisible))
    }

    @Test
    fun fileHttpAndInlineProvidersRemainVisible() {
        listOf("File", "HTTP", "Inline").forEach { vehicleType ->
            assertTrue(provider(vehicleType).let(ProviderDisplayPolicy::isVisible))
        }
    }

    @Test
    fun ruleProvidersUseTheSameCompatibleFilter() {
        assertTrue(provider(vehicleType = "HTTP", type = "Rule").let(ProviderDisplayPolicy::isVisible))
        assertFalse(provider(vehicleType = "Compatible", type = "Rule").let(ProviderDisplayPolicy::isVisible))
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

    private fun provider(vehicleType: String, type: String = "Proxy") = ProviderSnapshot(
        name = "test",
        type = type,
        vehicleType = vehicleType,
        updatedAtMillis = null,
    )
}
