package rs.chimera.android.service

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.BackendRuntimeState
import rs.chimera.android.backend.VpnCommandGate
import rs.chimera.android.backend.model.ServiceState

class VpnRuntimeCleanupCoordinatorTest {
    private val runtimeControl = FakeVpnRuntimeControl()

    @After
    fun resetGlobalState() {
        VpnRuntimeRegistry.unregister(runtimeControl)
        VpnRuntimeRegistry.requestStop()
        BackendRuntimeState.updateServiceState(ServiceState.STOPPED)
    }

    @Test
    fun shutdownFailureReportsErrorUnregistersRuntimeAndAllowsRetry() = runBlocking {
        val lifecycleGate = VpnLifecycleGate()
        assertTrue(lifecycleGate.requestStart())
        VpnRuntimeRegistry.requestStart()
        assertTrue(VpnRuntimeRegistry.register(runtimeControl))

        val shutdownFailure = IllegalStateException("core shutdown timed out")
        var releaseCalls = 0
        var publishedInactive = false
        val coordinator = VpnRuntimeCleanupCoordinator(
            lifecycleGate = lifecycleGate,
            runtimeControl = runtimeControl,
            releaseRuntime = { stopCore, removeForeground ->
                assertTrue(stopCore)
                assertTrue(removeForeground)
                releaseCalls++
                shutdownFailure
            },
            publishVpnInactive = { publishedInactive = true },
            formatReleaseFailure = { it.message.orEmpty() },
            logCleanupComplete = {},
        )

        val outcome = coordinator.cleanup()

        assertTrue(outcome.performed)
        assertSame(shutdownFailure, outcome.releaseFailure)
        assertTrue(publishedInactive)
        assertFalse(VpnRuntimeRegistry.isRegistered)
        assertEquals(ServiceState.ERROR, BackendRuntimeState.status.value.state)
        assertEquals("core shutdown timed out", BackendRuntimeState.status.value.error)

        var startAttempts = 0
        VpnCommandGate { BackendRuntimeState.serviceState.value }.start { startAttempts++ }
        assertEquals(1, startAttempts)

        assertFalse(coordinator.cleanup().performed)
        assertEquals(1, releaseCalls)
    }

    private class FakeVpnRuntimeControl : VpnRuntimeControl {
        override fun protectSocket(fd: Int): Boolean = false

        override fun onCoreStopped(message: String) = Unit

        override suspend fun stopVpn() = Unit

        override suspend fun restartVpn() = Unit
    }
}
