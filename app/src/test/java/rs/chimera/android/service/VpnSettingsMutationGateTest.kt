package rs.chimera.android.service

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
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
    fun productionRegistryRejectsSettingsWhileVpnIsRunning() {
        VpnRuntimeRegistry.requestStop()
        VpnDesiredStateRuntimeState.update(false)
        var actionInvoked = false

        try {
            val result = runCatching {
                VpnRuntimeRegistry.withSettingsMutation(
                    serviceState = ServiceState.RUNNING,
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
        VpnRuntimeRegistry.requestStop()
        VpnDesiredStateRuntimeState.update(false)
        val executor = Executors.newFixedThreadPool(2)
        val actionEntered = CountDownLatch(1)
        val releaseAction = CountDownLatch(1)
        val startRequestAttempted = CountDownLatch(1)
        val startThread = AtomicReference<Thread>()

        try {
            val update = executor.submit {
                VpnRuntimeRegistry.withSettingsMutation(ServiceState.STOPPED, { "unavailable" }) {
                    actionEntered.countDown()
                    assertTrue(releaseAction.await(5, TimeUnit.SECONDS))
                }
            }
            assertTrue(actionEntered.await(5, TimeUnit.SECONDS))

            val start = executor.submit {
                startThread.set(Thread.currentThread())
                startRequestAttempted.countDown()
                VpnRuntimeRegistry.requestStart {
                    VpnDesiredStateRuntimeState.update(true)
                }
            }
            assertTrue(startRequestAttempted.await(5, TimeUnit.SECONDS))
            assertTrue(awaitBlocked(startThread.get(), 5, TimeUnit.SECONDS))
            assertFalse(start.isDone)

            releaseAction.countDown()
            update.get(5, TimeUnit.SECONDS)
            start.get(5, TimeUnit.SECONDS)

            val result = runCatching {
                VpnRuntimeRegistry.withSettingsMutation(
                    ServiceState.STOPPED,
                    { "unavailable" },
                ) { "saved" }
            }
            assertEquals("unavailable", result.exceptionOrNull()?.message)
        } finally {
            releaseAction.countDown()
            executor.shutdownNow()
            VpnRuntimeRegistry.requestStop()
            VpnDesiredStateRuntimeState.update(false)
        }
    }

    private fun awaitBlocked(thread: Thread?, timeout: Long, unit: TimeUnit): Boolean {
        if (thread == null) return false
        val deadline = System.nanoTime() + unit.toNanos(timeout)
        while (System.nanoTime() < deadline) {
            if (thread.state == Thread.State.BLOCKED) return true
            if (!thread.isAlive) return false
            Thread.yield()
        }
        return thread.state == Thread.State.BLOCKED
    }
}
