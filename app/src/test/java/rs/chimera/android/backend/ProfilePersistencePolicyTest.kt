package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePersistencePolicyTest {
    @Test
    fun successfulCommitRunsAfterCommitAction() {
        var afterCommitCalled = false

        ProfilePersistencePolicy.commit(
            persist = { true },
            afterCommit = { afterCommitCalled = true },
        )

        assertTrue(afterCommitCalled)
    }

    @Test
    fun failedCommitDoesNotRunAfterCommitAction() {
        var afterCommitCalled = false

        assertThrows(IllegalStateException::class.java) {
            ProfilePersistencePolicy.commit(
                persist = { false },
                afterCommit = { afterCommitCalled = true },
            )
        }

        assertFalse(afterCommitCalled)
    }

    @Test
    fun failedCommitReportsStableError() {
        val error = assertThrows(IllegalStateException::class.java) {
            ProfilePersistencePolicy.commit(persist = { false })
        }

        assertEquals("Failed to persist profile catalog", error.message)
    }
}
