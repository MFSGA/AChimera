package rs.chimera.android.backend

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAutoUpdateStateCommitterTest {
    @Test
    fun matchingCurrentProfileCommitsState() = runBlocking {
        val current = remoteEntry(autoUpdate = true, lastUpdated = 42L)
        val state = boundState(current, revision = 42L)
        var committed = false

        commitAutoUpdateStateIfCurrent(
            state = state,
            readCurrent = { current },
            commit = { committed = true },
        )

        assertTrue(committed)
    }

    @Test
    fun disabledProfileRejectsStalePendingMark() = runBlocking {
        val original = remoteEntry(autoUpdate = true, lastUpdated = 42L)
        val state = boundState(original, revision = 42L)
        var committed = false

        commitAutoUpdateStateIfCurrent(
            state = state,
            readCurrent = { original.copy(autoUpdate = false) },
            commit = { committed = true },
        )

        assertFalse(committed)
    }

    @Test
    fun changedSourceRejectsStalePendingMarkAtSameRevision() = runBlocking {
        val original = remoteEntry(autoUpdate = true, lastUpdated = 42L)
        val state = boundState(original, revision = 42L)
        var committed = false

        commitAutoUpdateStateIfCurrent(
            state = state,
            readCurrent = {
                original.copy(url = "https://example.com/replacement.yaml")
            },
            commit = { committed = true },
        )

        assertFalse(committed)
    }

    @Test
    fun deletedProfileRejectsStalePendingMark() = runBlocking {
        val current = remoteEntry(autoUpdate = true, lastUpdated = 42L)
        val state = boundState(current, revision = 42L)
        var committed = false

        commitAutoUpdateStateIfCurrent(
            state = state,
            readCurrent = { throw IllegalArgumentException("missing") },
            commit = { committed = true },
        )

        assertFalse(committed)
    }

    @Test
    fun stalePendingMarkReportsCurrentPendingState() = runBlocking {
        val current = remoteEntry(autoUpdate = true, lastUpdated = 42L)
        val state = boundState(current, revision = 42L)
        var pending = false

        val hasPending = markRuntimeApplyPendingAndReadCurrent(
            state = state,
            readCurrent = { current },
            markPending = { /* stale state-store write is a successful no-op */ },
            readPending = { pending },
        )

        assertFalse(hasPending)
    }

    @Test
    fun concurrentPendingWriteIsObservedAfterStaleMark() = runBlocking {
        val current = remoteEntry(autoUpdate = true, lastUpdated = 42L)
        val state = boundState(current, revision = 42L)
        var pending = false

        val hasPending = markRuntimeApplyPendingAndReadCurrent(
            state = state,
            readCurrent = { current },
            markPending = { pending = true },
            readPending = { pending },
        )

        assertTrue(hasPending)
    }

    private fun remoteEntry(
        autoUpdate: Boolean,
        lastUpdated: Long,
    ) = RemoteProfileCatalogEntry(
        type = "REMOTE",
        url = "https://example.com/profile.yaml",
        autoUpdate = autoUpdate,
        userAgent = "Chimera",
        proxyUrl = null,
        filePath = "/tmp/profile.yaml",
        lastUpdated = lastUpdated,
    )

    private fun boundState(
        entry: RemoteProfileCatalogEntry,
        revision: Long,
    ) = ProfileAutoUpdateState(
        lastAttempt = 100L,
        failureCount = 0,
        nextAttemptAt = null,
        lastError = null,
        runtimeApplyPending = true,
        sourceFingerprint = ProfileAutoUpdatePolicy.sourceFingerprint(
            autoUpdate = entry.autoUpdate,
            url = entry.url,
            userAgent = entry.userAgent,
            proxyUrl = entry.proxyUrl,
        ),
        profileRevision = revision,
    )
}
