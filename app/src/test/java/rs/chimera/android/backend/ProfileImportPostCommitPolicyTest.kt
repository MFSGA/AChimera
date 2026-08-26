package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ProfileImportPostCommitPolicyTest {
    @Test
    fun restoreFailureDoesNotSkipScheduleSync() = runBlocking {
        val expected = IllegalStateException("restore failed")
        val failures = mutableListOf<Throwable>()
        var scheduleCalls = 0

        completeRemoteImportPostCommit(
            restoreActiveProfile = { throw expected },
            synchronizeSchedule = { scheduleCalls += 1 },
            onFailure = failures::add,
        )

        assertEquals(1, scheduleCalls)
        assertEquals(listOf(expected), failures)
    }

    @Test
    fun scheduleFailureIsReportedAfterSuccessfulRestore() = runBlocking {
        val expected = IllegalStateException("schedule failed")
        val failures = mutableListOf<Throwable>()
        var restoreCalls = 0

        completeRemoteImportPostCommit(
            restoreActiveProfile = { restoreCalls += 1 },
            synchronizeSchedule = { throw expected },
            onFailure = failures::add,
        )

        assertEquals(1, restoreCalls)
        assertEquals(listOf(expected), failures)
    }

    @Test
    fun fatalErrorIsNotSwallowedAsBestEffortFailure() = runBlocking {
        val expected = AssertionError("fatal")
        var scheduleCalls = 0

        try {
            completeRemoteImportPostCommit(
                restoreActiveProfile = { throw expected },
                synchronizeSchedule = { scheduleCalls += 1 },
                onFailure = { fail("fatal errors must not be reported as recoverable failures") },
            )
            fail("expected fatal error")
        } catch (error: AssertionError) {
            assertSame(expected, error)
        }

        assertEquals(0, scheduleCalls)
    }

    @Test
    fun cancellationStopsRemainingPostCommitSteps() = runBlocking {
        val expected = CancellationException("cancelled")
        var scheduleCalls = 0

        try {
            completeRemoteImportPostCommit(
                restoreActiveProfile = { throw expected },
                synchronizeSchedule = { scheduleCalls += 1 },
                onFailure = { fail("cancellation must not be reported as a normal failure") },
            )
            fail("expected cancellation")
        } catch (error: CancellationException) {
            assertSame(expected, error)
        }

        assertEquals(0, scheduleCalls)
    }
}
