package rs.chimera.android.service

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnRuntimeRegistryTest {
    private val registeredControls = mutableListOf<FakeVpnRuntimeControl>()

    @After
    fun tearDown() {
        registeredControls.forEach(VpnRuntimeRegistry::unregister)
    }

    @Test
    fun routesRuntimeCapabilitiesToRegisteredControl() = runBlocking {
        val control = register(FakeVpnRuntimeControl(protectResult = true))

        assertTrue(VpnRuntimeRegistry.isRegistered)
        assertTrue(VpnRuntimeRegistry.protectSocket(42))
        assertTrue(VpnRuntimeRegistry.dispatchCoreStopped("core failed"))
        assertTrue(VpnRuntimeRegistry.stopVpn())
        VpnRuntimeRegistry.restartVpn()

        assertEquals(listOf(42), control.protectedFds)
        assertEquals(listOf("core failed"), control.coreStopMessages)
        assertEquals(1, control.stopCalls)
        assertEquals(1, control.restartCalls)
    }

    @Test
    fun staleControlCannotUnregisterReplacement() {
        val first = register(FakeVpnRuntimeControl())
        val second = register(FakeVpnRuntimeControl())

        VpnRuntimeRegistry.unregister(first)

        assertTrue(VpnRuntimeRegistry.isRegistered)
        assertFalse(VpnRuntimeRegistry.protectSocket(7))
        assertEquals(listOf(7), second.protectedFds)
    }

    @Test
    fun unavailableRuntimeReturnsSafeFallbacks() = runBlocking {
        registeredControls.forEach(VpnRuntimeRegistry::unregister)

        assertFalse(VpnRuntimeRegistry.isRegistered)
        assertFalse(VpnRuntimeRegistry.protectSocket(1))
        assertFalse(VpnRuntimeRegistry.dispatchCoreStopped("ignored"))
        assertFalse(VpnRuntimeRegistry.stopVpn())
        val error = runCatching { VpnRuntimeRegistry.restartVpn() }.exceptionOrNull()
        assertEquals("VPN service is unavailable", error?.message)
    }

    private fun register(control: FakeVpnRuntimeControl): FakeVpnRuntimeControl {
        registeredControls += control
        VpnRuntimeRegistry.register(control)
        return control
    }

    private class FakeVpnRuntimeControl(
        private val protectResult: Boolean = false,
    ) : VpnRuntimeControl {
        val protectedFds = mutableListOf<Int>()
        val coreStopMessages = mutableListOf<String>()
        var stopCalls = 0
        var restartCalls = 0

        override fun protectSocket(fd: Int): Boolean {
            protectedFds += fd
            return protectResult
        }

        override fun onCoreStopped(message: String) {
            coreStopMessages += message
        }

        override suspend fun stopVpn() {
            stopCalls += 1
        }

        override suspend fun restartVpn() {
            restartCalls += 1
        }
    }
}
