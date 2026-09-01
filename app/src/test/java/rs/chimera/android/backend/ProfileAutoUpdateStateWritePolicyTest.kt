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
    fun sameRevisionRejectsOlderAttempt() {
        assertFalse(
            ProfileAutoUpdateStateWritePolicy.canReplace(
                boundState(revision = 2_000L, attemptedAt = 3_000L),
                boundState(revision = 2_000L, attemptedAt = 2_000L),
            ),
        )
    }

    @Test
    fun sameRevisionAcceptsNewerAttempt() {
        assertTrue(
            ProfileAutoUpdateStateWritePolicy.canReplace(
                boundState(revision = 2_000L, attemptedAt = 2_000L),
                boundState(revision = 2_000L, attemptedAt = 3_000L),
            ),
        )
    }

    @Test
    fun boundRefreshNowRejectsEqualRevisionCompletion() {
        val refreshNow = boundState(revision = 0L, attemptedAt = null).copy(nextAttemptAt = 3_000L)

        assertFalse(
            ProfileAutoUpdateStateWritePolicy.canReplace(
                refreshNow,
                boundState(revision = 0L, attemptedAt = 2_000L),
            ),
        )
    }

    @Test
    fun boundRefreshNowRejectsOlderRefreshNowState() {
        val current = boundState(revision = 0L, attemptedAt = null).copy(nextAttemptAt = 3_000L)
        val incoming = boundState(revision = 0L, attemptedAt = null).copy(nextAttemptAt = 2_000L)

        assertFalse(ProfileAutoUpdateStateWritePolicy.canReplace(current, incoming))
    }

    @Test
    fun boundRefreshNowAcceptsNewerRefreshNowState() {
        val current = boundState(revision = 0L, attemptedAt = null).copy(nextAttemptAt = 2_000L)
        val incoming = boundState(revision = 0L, attemptedAt = null).copy(nextAttemptAt = 3_000L)

        assertTrue(ProfileAutoUpdateStateWritePolicy.canReplace(current, incoming))
    }

    @Test
    fun boundRefreshNowPreservesRuntimeApplyPending() {
        val current = boundState(revision = 0L, attemptedAt = null).copy(
            nextAttemptAt = 2_000L,
            runtimeApplyPending = true,
        )
        val incoming = boundState(revision = 0L, attemptedAt = null).copy(nextAttemptAt = 3_000L)

        assertFalse(ProfileAutoUpdateStateWritePolicy.canReplace(current, incoming))
    }

    @Test
    fun sameRevisionAttemptPreservesRuntimeApplyPending() {
        val current = boundState(revision = 2_000L, attemptedAt = 3_000L).copy(runtimeApplyPending = true)
        val incoming = boundState(revision = 2_000L, attemptedAt = 3_000L)

        assertFalse(ProfileAutoUpdateStateWritePolicy.canReplace(current, incoming))
    }

    @Test
    fun sameRevisionNewerAttemptPreservesRuntimeApplyPending() {
        val current = boundState(revision = 2_000L, attemptedAt = 2_000L).copy(runtimeApplyPending = true)
        val incoming = boundState(revision = 2_000L, attemptedAt = 3_000L)

        assertFalse(ProfileAutoUpdateStateWritePolicy.canReplace(current, incoming))
    }

    @Test
    fun sameRevisionAttemptCanSetRuntimeApplyPending() {
        val current = boundState(revision = 2_000L, attemptedAt = 3_000L)
        val incoming = current.copy(runtimeApplyPending = true)

        assertTrue(ProfileAutoUpdateStateWritePolicy.canReplace(current, incoming))
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
        attemptedAt: Long? = 1_000L,
    ) = ProfileAutoUpdateState(
        lastAttempt = attemptedAt,
        failureCount = 0,
        nextAttemptAt = 2_000L,
        lastError = null,
        sourceFingerprint = source,
        profileRevision = revision,
    )
}
