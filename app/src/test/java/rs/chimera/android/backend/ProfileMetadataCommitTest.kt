package rs.chimera.android.backend

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMetadataCommitTest {
    @Test
    fun immediateRefreshFailureDoesNotEscapeCommittedSettings() {
        val failure = IllegalStateException("scheduler unavailable")
        val failures = mutableListOf<Throwable>()

        requestImmediateProfileRefreshBestEffort(
            request = { throw failure },
            onFailure = failures::add,
        )

        assertEquals(listOf(failure), failures)
    }

    @Test
    fun immediateRefreshFatalErrorPropagates() {
        val expected = AssertionError("fatal scheduler error")

        val actual = runCatching {
            requestImmediateProfileRefreshBestEffort(
                request = { throw expected },
                onFailure = { throw AssertionError("fatal error was downgraded", it) },
            )
        }.exceptionOrNull()

        assertTrue(actual === expected)
    }

    @Test
    fun refreshStateWriteFailureIsRetriedBeforeClearingStaleState() {
        val failure = IllegalStateException("state store unavailable")
        val failures = mutableListOf<Throwable>()
        var writeAttempts = 0
        var cleared = false

        writeProfileRefreshStateBestEffort(
            write = {
                writeAttempts += 1
                if (writeAttempts == 1) throw failure
            },
            clearStaleState = {
                cleared = true
                true
            },
            onFailure = failures::add,
        )

        assertEquals(2, writeAttempts)
        assertTrue(!cleared)
        assertEquals(listOf(failure), failures)
    }

    @Test
    fun repeatedRefreshStateWriteFailureClearsStaleState() {
        val firstFailure = IllegalStateException("state store unavailable")
        val retryFailure = IllegalStateException("state store still unavailable")
        val failures = mutableListOf<Throwable>()
        var writeAttempts = 0
        var cleared = false

        writeProfileRefreshStateBestEffort(
            write = {
                writeAttempts += 1
                throw if (writeAttempts == 1) firstFailure else retryFailure
            },
            clearStaleState = {
                cleared = true
                true
            },
            onFailure = failures::add,
        )

        assertEquals(2, writeAttempts)
        assertTrue(cleared)
        assertEquals(listOf(firstFailure, retryFailure), failures)
    }

    @Test
    fun refreshStateWriteFatalErrorPropagates() {
        val expected = AssertionError("fatal state store error")

        val actual = runCatching {
            writeProfileRefreshStateBestEffort(
                write = { throw expected },
                clearStaleState = { true },
                onFailure = { throw AssertionError("fatal error was downgraded", it) },
            )
        }.exceptionOrNull()

        assertTrue(actual === expected)
    }

    @Test
    fun staleStateClearFailureIsReportedWithoutEscaping() {
        val failure = IllegalStateException("state store unavailable")
        val failures = mutableListOf<Throwable>()

        clearProfileAutoUpdateStateBestEffort(
            clear = { throw failure },
            onFailure = failures::add,
        )

        assertEquals(listOf(failure), failures)
    }

    @Test
    fun staleStateClearFatalErrorPropagates() {
        val expected = AssertionError("fatal state store error")

        val actual = runCatching {
            clearProfileAutoUpdateStateBestEffort(
                clear = { throw expected },
                onFailure = { throw AssertionError("fatal error was downgraded", it) },
            )
        }.exceptionOrNull()

        assertTrue(actual === expected)
    }

    @Test
    fun committedMetadataRefreshFailureIsReportedWithoutEscaping() {
        val failure = IllegalStateException("catalog temporarily unavailable")
        val failures = mutableListOf<Throwable>()

        refreshCommittedProfileMetadataBestEffort(
            refresh = { throw failure },
            onFailure = failures::add,
        )

        assertEquals(listOf(failure), failures)
    }

    @Test
    fun committedMetadataRefreshFatalErrorPropagates() {
        val expected = AssertionError("fatal catalog error")

        val actual = runCatching {
            refreshCommittedProfileMetadataBestEffort(
                refresh = { throw expected },
                onFailure = { throw AssertionError("fatal error was downgraded", it) },
            )
        }.exceptionOrNull()

        assertTrue(actual === expected)
    }

    @Test
    fun committedSettingsFinishScheduleSyncAfterCallerCancellation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var completed = false

        val job = launch {
            completeProfileMetadataCommit {
                started.complete(Unit)
                release.await()
                completed = true
            }
        }

        started.await()
        job.cancel()
        release.complete(Unit)
        job.join()

        assertTrue(completed)
        assertTrue(job.isCancelled)
    }

    @Test
    fun committedRenameFinishesPresentationRefreshAfterCallerCancellation() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var completed = false

        val job = launch {
            completeProfileMetadataCommit {
                started.complete(Unit)
                release.await()
                completed = true
            }
        }

        started.await()
        job.cancel()
        release.complete(Unit)
        job.join()

        assertTrue(completed)
        assertTrue(job.isCancelled)
    }
}
