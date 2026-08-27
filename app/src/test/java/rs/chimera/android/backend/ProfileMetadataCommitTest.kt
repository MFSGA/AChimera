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
    fun refreshStateWriteFailureIsReported() {
        val failure = IllegalStateException("state store unavailable")
        val failures = mutableListOf<Throwable>()

        writeProfileRefreshStateBestEffort(
            write = { throw failure },
            onFailure = failures::add,
        )

        assertEquals(listOf(failure), failures)
    }

    @Test
    fun refreshStateWriteFatalErrorPropagates() {
        val expected = AssertionError("fatal state store error")

        val actual = runCatching {
            writeProfileRefreshStateBestEffort(
                write = { throw expected },
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
