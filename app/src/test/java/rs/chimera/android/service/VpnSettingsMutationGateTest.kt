package rs.chimera.android.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class VpnSettingsMutationGateTest {
    @Test
    fun settingsWriteRejectsActiveAndPendingVpn() {
        val lock = Any()
        val runRequested = AtomicBoolean(false)
        val desiredRunRequested = AtomicBoolean(false)
        val gate = VpnSettingsMutationGate(lock) {
            runRequested.get() || desiredRunRequested.get()
        }
        var actionInvoked = false

        val runningResult = runCatching {
            gate.withSettingsMutation(ServiceState.RUNNING, { "unavailable" }) {
                actionInvoked = true
                "saved"
            }
        }
        assertEquals("unavailable", runningResult.exceptionOrNull()?.message)

        desiredRunRequested.set(true)
        val pendingResult = runCatching {
            gate.withSettingsMutation(ServiceState.STOPPED, { "unavailable" }) {
                actionInvoked = true
                "saved"
            }
        }
        assertEquals("unavailable", pendingResult.exceptionOrNull()?.message)
        assertFalse(actionInvoked)
    }

    @Test
    fun productionRegistryRejectsSettingsAfterStartIntentIsPersisted() {
        VpnRuntimeRegistry.requestStop()
        VpnDesiredStateRuntimeState.update(false)
        var actionInvoked = false

        try {
            VpnRuntimeRegistry.requestStart {
                VpnDesiredStateRuntimeState.update(true)
            }

            val result = runCatching {
                VpnRuntimeRegistry.withSettingsMutation(
                    serviceState = ServiceState.STOPPED,
                    unavailableMessage = { "unavailable" },
                ) {
                    actionInvoked = true
                    "saved"
                }
            }

            assertEquals("unavailable", result.exceptionOrNull()?.message)
            assertFalse(actionInvoked)
        } finally {
            VpnRuntimeRegistry.requestStop()
            VpnDesiredStateRuntimeState.update(false)
        }
    }

    @Test
    fun settingsWriteSerializesWithVpnStartRequest() {
        val lock = Any()
        val runRequested = AtomicBoolean(false)
        val gate = VpnSettingsMutationGate(lock, runRequested::get)
        val executor = Executors.newFixedThreadPool(2)
        val actionEntered = CountDownLatch(1)
        val releaseAction = CountDownLatch(1)
        val startRequestStarted = CountDownLatch(1)

        try {
            val update = executor.submit {
                gate.withSettingsMutation(ServiceState.STOPPED, { "unavailable" }) {
                    actionEntered.countDown()
                    assertTrue(releaseAction.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(actionEntered.await(5, TimeUnit.SECONDS))

            val start = executor.submit {
                synchronized(lock) {
                    startRequestStarted.countDown()
                    runRequested.set(true)
                }
            }
            assertFalse(startRequestStarted.await(100, TimeUnit.MILLISECONDS))
            assertFalse(start.isDone)

            releaseAction.countDown()
            assertTrue(startRequestStarted.await(5, TimeUnit.SECONDS))
            update.get(5, TimeUnit.SECONDS)
            start.get(5, TimeUnit.SECONDS)

            val result = runCatching {
                gate.withSettingsMutation(ServiceState.STOPPED, { "unavailable" }) { "saved" }
            }
            assertEquals("unavailable", result.exceptionOrNull()?.message)
        } finally {
            releaseAction.countDown()
            executor.shutdownNow()
        }
    }
}
