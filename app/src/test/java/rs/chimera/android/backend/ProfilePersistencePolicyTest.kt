package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePersistencePolicyTest {
    @Test
    fun successfulCommitReturnsNormally() {
        ProfilePersistencePolicy.commit(persist = { true })
    }

    @Test
    fun failedCommitReportsStableError() {
        val error = assertThrows(IllegalStateException::class.java) {
            ProfilePersistencePolicy.commit(persist = { false })
        }

        assertEquals("Failed to persist profile catalog", error.message)
    }

    @Test
    fun requiredCommitRetriesOnceAfterTransientFailure() {
        var attempts = 0

        ProfilePersistencePolicy.commitWithRetry {
            attempts += 1
            attempts == 2
        }

        assertEquals(2, attempts)
    }

    @Test
    fun requiredCommitReportsFailureAfterRetry() {
        var attempts = 0

        val error = assertThrows(IllegalStateException::class.java) {
            ProfilePersistencePolicy.commitWithRetry {
                attempts += 1
                false
            }
        }

        assertEquals("Failed to persist profile catalog", error.message)
        assertEquals(2, attempts)
    }

    @Test
    fun bestEffortCommitRetriesOnceAfterTransientFailure() {
        var attempts = 0

        val result = ProfilePersistencePolicy.tryCommitWithRetry {
            attempts += 1
            attempts == 2
        }

        assertTrue(result)
        assertEquals(2, attempts)
    }

    @Test
    fun bestEffortCommitStopsAfterSecondFailure() {
        var attempts = 0

        val result = ProfilePersistencePolicy.tryCommitWithRetry {
            attempts += 1
            false
        }

        assertFalse(result)
        assertEquals(2, attempts)
    }
}
