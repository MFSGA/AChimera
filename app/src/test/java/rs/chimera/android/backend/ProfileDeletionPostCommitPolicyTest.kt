package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ProfileDeletionPostCommitPolicyTest {
    @Test
    fun restoreFailureDoesNotSkipRemainingPostCommitSteps() {
        val expected = IllegalStateException("restore failed")
        val failures = mutableListOf<Throwable>()
        var clearCalls = 0
        var refreshCalls = 0

        completeProfileDeletionPostCommit(
            restoreActivePath = { throw expected },
            clearAutoUpdateState = {
                clearCalls += 1
                true
            },
            refreshActiveProfile = { refreshCalls += 1 },
            onFailure = failures::add,
        )

        assertEquals(1, clearCalls)
        assertEquals(1, refreshCalls)
        assertEquals(listOf(expected), failures)
    }

    @Test
    fun clearFailureDoesNotSkipActiveProfileRefresh() {
        val expected = IllegalStateException("clear failed")
        val failures = mutableListOf<Throwable>()
        var refreshCalls = 0

        completeProfileDeletionPostCommit(
            restoreActivePath = {},
            clearAutoUpdateState = { throw expected },
            refreshActiveProfile = { refreshCalls += 1 },
            onFailure = failures::add,
        )

        assertEquals(1, refreshCalls)
        assertEquals(listOf(expected), failures)
    }

    @Test
    fun rejectedStateClearIsReportedWithoutFailingCompletion() {
        val failures = mutableListOf<Throwable>()
        var refreshCalls = 0

        completeProfileDeletionPostCommit(
            restoreActivePath = {},
            clearAutoUpdateState = { false },
            refreshActiveProfile = { refreshCalls += 1 },
            onFailure = failures::add,
        )

        assertEquals(1, failures.size)
        assertEquals("Failed to clear deleted auto-update state", failures.single().message)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun fatalErrorStopsPostCommitCompletion() {
        val expected = AssertionError("fatal")
        var clearCalls = 0
        var refreshCalls = 0

        try {
            completeProfileDeletionPostCommit(
                restoreActivePath = { throw expected },
                clearAutoUpdateState = {
                    clearCalls += 1
                    true
                },
                refreshActiveProfile = { refreshCalls += 1 },
                onFailure = { fail("fatal errors must not be reported as recoverable failures") },
            )
            fail("expected fatal error")
        } catch (error: AssertionError) {
            assertSame(expected, error)
        }

        assertEquals(0, clearCalls)
        assertEquals(0, refreshCalls)
    }
}
