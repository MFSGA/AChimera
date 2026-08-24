package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileAutoUpdateLegacyBindingPolicyTest {
    @Test
    fun persistenceFailureKeepsBoundStateForCurrentRead() {
        val boundState = ProfileAutoUpdateState(
            lastAttempt = null,
            failureCount = 0,
            nextAttemptAt = 1_000L,
            lastError = null,
            sourceFingerprint = "source",
            profileRevision = 100L,
        )

        val resolved = ProfileAutoUpdateLegacyBindingPolicy.persistBestEffort(boundState) {
            throw IllegalStateException("preferences unavailable")
        }

        assertEquals(boundState, resolved)
    }

    @Test
    fun concurrentStateChangeStillRejectsLegacyBinding() {
        val boundState = ProfileAutoUpdateState(
            lastAttempt = null,
            failureCount = 0,
            nextAttemptAt = 1_000L,
            lastError = null,
            sourceFingerprint = "source",
            profileRevision = 100L,
        )

        val resolved = ProfileAutoUpdateLegacyBindingPolicy.persistBestEffort(boundState) { null }

        assertNull(resolved)
    }

    @Test
    fun rejectedBindingCanUseNewerMatchingState() {
        val profile = remoteProfile()
        val currentState = ProfileAutoUpdatePolicy.bindStateToSource(
            profile = profile,
            state = ProfileAutoUpdateState(
                lastAttempt = 900L,
                failureCount = 2,
                nextAttemptAt = 1_200L,
                lastError = "IOException",
                runtimeApplyPending = true,
            ),
        )

        val resolved = ProfileAutoUpdateLegacyBindingPolicy.resolveCurrentStateAfterRejectedBinding(
            profile = profile,
            currentState = currentState,
        )

        assertEquals(currentState, resolved)
    }

    @Test
    fun rejectedBindingDoesNotUseStateForDifferentRevision() {
        val profile = remoteProfile(lastUpdated = 100L)
        val currentState = ProfileAutoUpdatePolicy.bindStateToSource(
            profile = profile.copy(lastUpdated = 101L),
            state = ProfileAutoUpdateState(
                lastAttempt = 900L,
                failureCount = 2,
                nextAttemptAt = 1_200L,
                lastError = "IOException",
            ),
        )

        val resolved = ProfileAutoUpdateLegacyBindingPolicy.resolveCurrentStateAfterRejectedBinding(
            profile = profile,
            currentState = currentState,
        )

        assertNull(resolved)
    }

    @Test
    fun matchingSnapshotCanBindLegacyState() {
        assertTrue(
            ProfileAutoUpdateLegacyBindingPolicy.snapshotStillMatchesCurrentCatalog(
                snapshot = remoteProfile(),
                current = currentEntry(),
            ),
        )
    }

    @Test
    fun changedSourceCannotBindLegacyState() {
        assertFalse(
            ProfileAutoUpdateLegacyBindingPolicy.snapshotStillMatchesCurrentCatalog(
                snapshot = remoteProfile(url = "https://old.example/profile.yaml"),
                current = currentEntry(url = "https://new.example/profile.yaml"),
            ),
        )
    }

    @Test
    fun changedRevisionCannotBindLegacyState() {
        assertFalse(
            ProfileAutoUpdateLegacyBindingPolicy.snapshotStillMatchesCurrentCatalog(
                snapshot = remoteProfile(lastUpdated = 100L),
                current = currentEntry(lastUpdated = 101L),
            ),
        )
    }

    private fun remoteProfile(
        url: String = "https://example.test/profile.yaml",
        lastUpdated: Long? = 100L,
    ) = ProfileSummary(
        id = "remote",
        name = "remote",
        filePath = "/profiles/remote.yaml",
        type = ProfileType.REMOTE,
        isActive = false,
        isRemote = true,
        lastUpdated = lastUpdated,
        fileSize = 1,
        url = url,
        autoUpdate = true,
        userAgent = "Chimera",
        proxyUrl = "http://127.0.0.1:7890",
    )

    private fun currentEntry(
        url: String = "https://example.test/profile.yaml",
        lastUpdated: Long? = 100L,
    ) = RemoteProfileCatalogEntry(
        type = "REMOTE",
        url = url,
        autoUpdate = true,
        userAgent = "Chimera",
        proxyUrl = "http://127.0.0.1:7890",
        filePath = "/profiles/remote.yaml",
        lastUpdated = lastUpdated,
    )
}
