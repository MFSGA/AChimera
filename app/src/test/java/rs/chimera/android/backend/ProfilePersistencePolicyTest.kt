package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
}
