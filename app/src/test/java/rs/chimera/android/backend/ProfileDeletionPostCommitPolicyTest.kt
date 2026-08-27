package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ProfileDeletionPostCommitPolicyTest {
    @Test
    fun committedActivePathUpdatesBeforeRemainingPostCommitSteps() {
        val events = mutableListOf<String>()
        var runtimePath: String? = "deleted"

        completeProfileDeletionPostCommit(
            activePath = "remaining",
            updateRuntimePath = {
                runtimePath = it
                events += "path"
            },
            clearAutoUpdateState = {
                events += "clear"
                true
            },
            refreshActiveProfile = { events += "refresh" },
            onFailure = { fail("unexpected failure: $it") },
        )

        assertEquals("remaining", runtimePath)
        assertEquals(listOf("path", "clear", "refresh"), events)
    }

    @Test
    fun emptyCatalogClearsRuntimePathBeforeRemainingPostCommitSteps() {
        var runtimePath: String? = "deleted"

        completeProfileDeletionPostCommit(
            activePath = null,
            updateRuntimePath = { runtimePath = it },
            clearAutoUpdateState = { true },
            refreshActiveProfile = {},
            onFailure = { fail("unexpected failure: $it") },
        )

        assertEquals(null, runtimePath)
    }

    @Test
    fun clearFailureDoesNotSkipActiveProfileRefresh() {
        val expected = IllegalStateException("clear failed")
        val failures = mutableListOf<Throwable>()
        var refreshCalls = 0

        completeProfileDeletionPostCommit(
            activePath = "remaining",
            updateRuntimePath = {},
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
            activePath = "remaining",
            updateRuntimePath = {},
            clearAutoUpdateState = { false },
            refreshActiveProfile = { refreshCalls += 1 },
            onFailure = failures::add,
        )

        assertEquals(1, failures.size)
        assertEquals("Failed to clear deleted auto-update state", failures.single().message)
        assertEquals(1, refreshCalls)
    }

    @Test
    fun fatalStateClearStopsPostCommitCompletion() {
        val expected = AssertionError("fatal")
        var refreshCalls = 0

        try {
            completeProfileDeletionPostCommit(
                activePath = "remaining",
                updateRuntimePath = {},
                clearAutoUpdateState = { throw expected },
                refreshActiveProfile = { refreshCalls += 1 },
                onFailure = { fail("fatal errors must not be reported as recoverable failures") },
            )
            fail("expected fatal error")
        } catch (error: AssertionError) {
            assertSame(expected, error)
        }

        assertEquals(0, refreshCalls)
    }
}
