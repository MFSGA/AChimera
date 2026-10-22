package rs.chimera.android.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ProxyMode

class SessionProxyModeCoordinatorTest {
    @Test
    fun clearingOverrideRestoresTheModeCapturedAtFirstChange() = runBlocking {
        var runtimeMode = ProxyMode.RULE
        var reads = 0
        val coordinator = SessionProxyModeCoordinator(
            readCurrentMode = {
                reads++
                runtimeMode
            },
            applyMode = { runtimeMode = it },
        )

        coordinator.setMode(ProxyMode.DIRECT)

        assertEquals(ProxyMode.DIRECT, runtimeMode)
        assertEquals(ProxyMode.DIRECT, coordinator.override.value)

        coordinator.setMode(ProxyMode.GLOBAL)
        coordinator.setMode(null)

        assertEquals(ProxyMode.RULE, runtimeMode)
        assertEquals(null, coordinator.override.value)
        assertEquals(1, reads)
    }

    @Test
    fun failedModeChangeDoesNotPublishAnOverride() = runBlocking {
        var runtimeMode = ProxyMode.RULE
        val coordinator = SessionProxyModeCoordinator(
            readCurrentMode = { runtimeMode },
            applyMode = {
                if (it == ProxyMode.DIRECT) error("mode update failed")
                runtimeMode = it
            },
        )

        runCatching { coordinator.setMode(ProxyMode.DIRECT) }

        assertEquals(ProxyMode.RULE, runtimeMode)
        assertEquals(null, coordinator.override.value)
    }

    @Test
    fun resettingSessionCapturesTheNextSessionsDefaultMode() = runBlocking {
        var runtimeMode = ProxyMode.RULE
        val coordinator = SessionProxyModeCoordinator(
            readCurrentMode = { runtimeMode },
            applyMode = { runtimeMode = it },
        )

        coordinator.setMode(ProxyMode.DIRECT)
        coordinator.resetSession()
        runtimeMode = ProxyMode.GLOBAL
        coordinator.setMode(ProxyMode.RULE)
        coordinator.setMode(null)

        assertEquals(ProxyMode.GLOBAL, runtimeMode)
        assertEquals(null, coordinator.override.value)
    }
}
