package rs.chimera.android.backend

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileRemoteUpdateCommitTest {
    @Test
    fun commitTimestampAdvancesWhenClockDoesNot() {
        assertEquals(1_001L, nextRemoteProfileCommitTimestamp(previous = 1_000L, now = 1_000L))
        assertEquals(1_001L, nextRemoteProfileCommitTimestamp(previous = 1_000L, now = 999L))
        assertEquals(2_000L, nextRemoteProfileCommitTimestamp(previous = 1_000L, now = 2_000L))
        assertEquals(2_000L, nextRemoteProfileCommitTimestamp(previous = null, now = 2_000L))
    }

    @Test
    fun runtimePathSynchronizesBeforeRefreshAndCommittedUpdateCallback() = runBlocking {
        val events = mutableListOf<String>()

        completeRemoteProfileUpdateCommit(
            clearAutoUpdateState = { true },
            synchronizeRuntimePath = { events += "path" },
            refreshActiveProfile = { events += "refresh" },
            afterCommit = { events += "commit" },
            onMaintenanceFailure = { fail("maintenance should succeed") },
        )

        assertEquals(listOf("path", "refresh", "commit"), events)
    }

    @Test
    fun runtimePathFailureDoesNotSkipCommittedUpdateCallback() = runBlocking {
        val expected = IllegalStateException("path failed")
        val failures = mutableListOf<Throwable>()
        var completed = false

        completeRemoteProfileUpdateCommit(
            clearAutoUpdateState = { true },
            synchronizeRuntimePath = { throw expected },
            refreshActiveProfile = {},
            afterCommit = { completed = true },
            onMaintenanceFailure = failures::add,
        )

        assertEquals(listOf(expected), failures)
        assertTrue(completed)
    }

    @Test
    fun maintenanceFailureDoesNotSkipCommittedUpdateCallback() = runBlocking {
        val expected = IllegalStateException("refresh failed")
        val failures = mutableListOf<Throwable>()
        var completed = false

        completeRemoteProfileUpdateCommit(
            clearAutoUpdateState = { true },
            synchronizeRuntimePath = {},
            refreshActiveProfile = { throw expected },
            afterCommit = { completed = true },
            onMaintenanceFailure = failures::add,
        )

        assertEquals(listOf(expected), failures)
        assertTrue(completed)
    }

    @Test
    fun rejectedStateClearDoesNotSkipCommittedUpdateCallback() = runBlocking {
        val failures = mutableListOf<Throwable>()
        var completed = false

        completeRemoteProfileUpdateCommit(
            clearAutoUpdateState = { false },
            synchronizeRuntimePath = {},
            refreshActiveProfile = {},
            afterCommit = { completed = true },
            onMaintenanceFailure = failures::add,
        )

        assertEquals(1, failures.size)
        assertEquals("Failed to clear updated auto-update state", failures.single().message)
        assertTrue(completed)
    }

    @Test
    fun committedUpdateFinishesPostCommitWorkAfterCallerCancellation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var completed = false

        val job = launch {
            completeRemoteProfileUpdateCommit(
                clearAutoUpdateState = { true },
                synchronizeRuntimePath = {},
                refreshActiveProfile = {},
                afterCommit = {
                    started.complete(Unit)
                    release.await()
                    completed = true
                },
                onMaintenanceFailure = { fail("maintenance should succeed") },
            )
        }

        started.await()
        job.cancel()
        release.complete(Unit)
        job.join()

        assertTrue(completed)
        assertTrue(job.isCancelled)
    }

    @Test
    fun fatalMaintenanceErrorPropagatesBeforeAfterCommit() = runBlocking {
        val expected = AssertionError("fatal")
        var completed = false

        try {
            completeRemoteProfileUpdateCommit(
                clearAutoUpdateState = { throw expected },
                synchronizeRuntimePath = {},
                refreshActiveProfile = {},
                afterCommit = { completed = true },
                onMaintenanceFailure = { fail("fatal error must not be downgraded") },
            )
            fail("expected fatal error")
        } catch (error: AssertionError) {
            assertEquals(expected.message, error.message)
        }

        assertTrue(!completed)
    }
}
