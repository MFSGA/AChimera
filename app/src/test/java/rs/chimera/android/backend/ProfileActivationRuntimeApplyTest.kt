package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.service.VpnStopInProgressException

class ProfileActivationRuntimeApplyTest {
    @Test
    fun activationSelectionUpdatesRuntimePathBeforeRefresh() {
        var runtimePath: String? = "old"
        val events = mutableListOf<String>()

        applyProfileActivationSelection(
            activePath = "new",
            updateRuntimePath = {
                runtimePath = it
                events += "path"
            },
            refreshActiveProfile = { events += "refresh" },
        )

        assertEquals("new", runtimePath)
        assertEquals(listOf("path", "refresh"), events)
    }

    @Test
    fun activationSelectionKeepsRuntimePathWhenRefreshFails() {
        var runtimePath: String? = "new"
        val expected = IllegalStateException("refresh failed")

        val error = runCatching {
            applyProfileActivationSelection(
                activePath = "old",
                updateRuntimePath = { runtimePath = it },
                refreshActiveProfile = { throw expected },
            )
        }.exceptionOrNull()

        assertSame(expected, error)
        assertEquals("old", runtimePath)
    }

    @Test
    fun selectionRestoreFailureRollsBackCommittedActivation() {
        val expected = IllegalStateException("restore failed")
        val events = mutableListOf<String>()

        val error = runCatching {
            commitProfileActivationSelection(
                persistActivation = { events += "persist" },
                restoreSelection = {
                    events += "restore"
                    throw expected
                },
                rollbackActivation = { events += "rollback" },
            )
        }.exceptionOrNull()

        assertSame(expected, error)
        assertEquals(listOf("persist", "restore", "rollback"), events)
    }

    @Test
    fun selectionRollbackFailureIsSuppressedOnOriginalError() {
        val expected = IllegalStateException("restore failed")
        val rollbackFailure = IllegalStateException("rollback failed")

        val error = runCatching {
            commitProfileActivationSelection(
                persistActivation = {},
                restoreSelection = { throw expected },
                rollbackActivation = { throw rollbackFailure },
            )
        }.exceptionOrNull()

        assertSame(expected, error)
        assertEquals(listOf(rollbackFailure), error?.suppressed?.toList())
    }

    @Test
    fun selectionRollbackFatalErrorPropagates() {
        val restoreFailure = IllegalStateException("restore failed")
        val fatalRollbackFailure = AssertionError("rollback failed")

        val error = runCatching {
            commitProfileActivationSelection(
                persistActivation = {},
                restoreSelection = { throw restoreFailure },
                rollbackActivation = { throw fatalRollbackFailure },
            )
        }.exceptionOrNull()

        assertSame(fatalRollbackFailure, error)
    }

    @Test
    fun committedActivationCompletesAfterCallerCancellation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var completed = false

        val activation = launch {
            runProfileActivationTransaction {
                entered.complete(Unit)
                release.await()
                completed = true
            }
        }
        entered.await()

        activation.cancel()
        release.complete(Unit)
        activation.join()

        assertTrue(completed)
        assertTrue(activation.isCancelled)
    }

    @Test
    fun runningVpnRestartsWithoutRollback() = runBlocking {
        var restarts = 0
        var rollbacks = 0

        applyProfileActivationToRunningVpn(
            serviceState = MutableStateFlow(ServiceState.RUNNING),
            restartVpn = { restarts++ },
            rollbackActivation = { rollbacks++ },
        )

        assertEquals(1, restarts)
        assertEquals(0, rollbacks)
    }

    @Test
    fun startingVpnRestartsAfterEnteringRunning() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.STARTING)
        var restarts = 0
        var rollbacks = 0

        val apply = async {
            applyProfileActivationToRunningVpn(
                serviceState = serviceState,
                restartVpn = { restarts++ },
                rollbackActivation = { rollbacks++ },
            )
        }
        yield()

        assertEquals(0, restarts)
        serviceState.value = ServiceState.RUNNING
        apply.await()

        assertEquals(1, restarts)
        assertEquals(0, rollbacks)
    }

    @Test
    fun startingVpnThatStopsRequiresNoRuntimeApply() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.STARTING)
        var restarts = 0
        var rollbacks = 0

        val apply = async {
            applyProfileActivationToRunningVpn(
                serviceState = serviceState,
                restartVpn = { restarts++ },
                rollbackActivation = { rollbacks++ },
            )
        }
        yield()

        serviceState.value = ServiceState.STOPPED
        apply.await()

        assertEquals(0, restarts)
        assertEquals(0, rollbacks)
    }

    @Test
    fun stoppedVpnRequiresNoRuntimeApply() = runBlocking {
        var restarts = 0
        var rollbacks = 0

        applyProfileActivationToRunningVpn(
            serviceState = MutableStateFlow(ServiceState.STOPPED),
            restartVpn = { restarts++ },
            rollbackActivation = { rollbacks++ },
        )

        assertEquals(0, restarts)
        assertEquals(0, rollbacks)
    }

    @Test
    fun restartFailureRollsBackActivationAndPropagates() = runBlocking {
        val expected = IllegalStateException("restart failed")
        var rollbacks = 0

        val error = runCatching {
            applyProfileActivationToRunningVpn(
                serviceState = MutableStateFlow(ServiceState.RUNNING),
                restartVpn = { throw expected },
                rollbackActivation = { rollbacks++ },
            )
        }.exceptionOrNull()

        assertSame(expected, error)
        assertEquals(1, rollbacks)
    }

    @Test
    fun restartFailurePreservesOriginalWhenRollbackFails() = runBlocking {
        val restartFailure = IllegalStateException("restart failed")
        val rollbackFailure = IllegalStateException("rollback failed")

        val error = runCatching {
            applyProfileActivationToRunningVpn(
                serviceState = MutableStateFlow(ServiceState.RUNNING),
                restartVpn = { throw restartFailure },
                rollbackActivation = { throw rollbackFailure },
            )
        }.exceptionOrNull()

        assertSame(restartFailure, error)
        assertEquals(listOf(rollbackFailure), error?.suppressed?.toList())
    }

    @Test
    fun restartFailureDuringStopKeepsActivationForNextStart() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.RUNNING)
        var rollbacks = 0

        applyProfileActivationToRunningVpn(
            serviceState = serviceState,
            restartVpn = {
                serviceState.value = ServiceState.STOPPING
                throw IllegalStateException("restart interrupted by stop")
            },
            rollbackActivation = { rollbacks++ },
        )

        assertEquals(0, rollbacks)
    }

    @Test
    fun restartFailureAfterRuntimeErrorKeepsActivationForNextStart() = runBlocking {
        val serviceState = MutableStateFlow(ServiceState.RUNNING)
        var rollbacks = 0

        applyProfileActivationToRunningVpn(
            serviceState = serviceState,
            restartVpn = {
                serviceState.value = ServiceState.ERROR
                throw IllegalStateException("restart failed after runtime error")
            },
            rollbackActivation = { rollbacks++ },
        )

        assertEquals(0, rollbacks)
    }

    @Test
    fun stopInProgressRestartRejectionKeepsActivationForNextStart() = runBlocking {
        var rollbacks = 0

        applyProfileActivationToRunningVpn(
            serviceState = MutableStateFlow(ServiceState.RUNNING),
            restartVpn = { throw VpnStopInProgressException() },
            rollbackActivation = { rollbacks++ },
        )

        assertEquals(0, rollbacks)
    }

    @Test
    fun restartCancellationDoesNotRollbackActivation() = runBlocking {
        var rollbacks = 0

        val error = runCatching {
            applyProfileActivationToRunningVpn(
                serviceState = MutableStateFlow(ServiceState.RUNNING),
                restartVpn = { throw CancellationException("cancelled") },
                rollbackActivation = { rollbacks++ },
            )
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(0, rollbacks)
    }
}
