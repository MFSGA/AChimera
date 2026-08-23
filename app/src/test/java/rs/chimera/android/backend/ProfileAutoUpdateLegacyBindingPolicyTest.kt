package rs.chimera.android.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileAutoUpdateLegacyBindingPolicyTest {
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
