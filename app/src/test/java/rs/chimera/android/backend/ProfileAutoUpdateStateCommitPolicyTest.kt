package rs.chimera.android.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAutoUpdateStateCommitPolicyTest {
    @Test
    fun matchingEnabledSourceAndRevisionCanRecord() {
        val current = remoteEntry(autoUpdate = true, lastUpdated = 42L)
        val state = boundState(current, revision = 42L)

        assertTrue(ProfileAutoUpdateStateCommitPolicy.canRecord(current, state))
    }

    @Test
    fun disabledAutoUpdateRejectsStaleCompletion() {
        val original = remoteEntry(autoUpdate = true, lastUpdated = 42L)
        val state = boundState(original, revision = 42L)
        val disabled = original.copy(autoUpdate = false)

        assertFalse(ProfileAutoUpdateStateCommitPolicy.canRecord(disabled, state))
    }

    @Test
    fun changedSourceRejectsStaleCompletion() {
        val original = remoteEntry(autoUpdate = true, lastUpdated = 42L)
        val state = boundState(original, revision = 42L)
        val changed = original.copy(url = "https://example.com/new.yaml")

        assertFalse(ProfileAutoUpdateStateCommitPolicy.canRecord(changed, state))
    }

    @Test
    fun changedRevisionRejectsStaleCompletion() {
        val current = remoteEntry(autoUpdate = true, lastUpdated = 43L)
        val state = boundState(current, revision = 42L)

        assertFalse(ProfileAutoUpdateStateCommitPolicy.canRecord(current, state))
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
        sourceFingerprint = ProfileAutoUpdatePolicy.sourceFingerprint(
            autoUpdate = entry.autoUpdate,
            url = entry.url,
            userAgent = entry.userAgent,
            proxyUrl = entry.proxyUrl,
        ),
        profileRevision = revision,
    )
}
