package rs.chimera.android.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAutoUpdateStateWritePolicyTest {
    @Test
    fun emptyStateAcceptsBoundState() {
        assertTrue(ProfileAutoUpdateStateWritePolicy.canReplace(emptyState(), boundState(revision = 2_000L)))
    }

    @Test
    fun sameSourceAcceptsNewerRevision() {
        assertTrue(
            ProfileAutoUpdateStateWritePolicy.canReplace(
                boundState(revision = 1_000L),
                boundState(revision = 2_000L),
            ),
        )
    }

    @Test
    fun newerRevisionRejectsStaleCompletion() {
        assertFalse(
            ProfileAutoUpdateStateWritePolicy.canReplace(
                boundState(revision = 3_000L),
                boundState(revision = 2_000L),
            ),
        )
    }

    @Test
    fun differentSourceRejectsStaleCompletion() {
        assertFalse(
            ProfileAutoUpdateStateWritePolicy.canReplace(
                boundState(revision = 2_000L, source = "new-source"),
                boundState(revision = 2_000L, source = "old-source"),
            ),
        )
    }

    @Test
    fun refreshNowStateRejectsBoundStaleCompletion() {
        val refreshNow = ProfileAutoUpdateState(
            lastAttempt = null,
            failureCount = 0,
            nextAttemptAt = 3_000L,
            lastError = null,
        )

        assertFalse(
            ProfileAutoUpdateStateWritePolicy.canReplace(
                refreshNow,
                boundState(revision = 2_000L),
            ),
        )
    }

    private fun emptyState() = ProfileAutoUpdateState(
        lastAttempt = null,
        failureCount = 0,
        nextAttemptAt = null,
        lastError = null,
    )

    private fun boundState(
        revision: Long,
        source: String = "source-a",
    ) = ProfileAutoUpdateState(
        lastAttempt = 1_000L,
        failureCount = 0,
        nextAttemptAt = 2_000L,
        lastError = null,
        sourceFingerprint = source,
        profileRevision = revision,
    )
}
