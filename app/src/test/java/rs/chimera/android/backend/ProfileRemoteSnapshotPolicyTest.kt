package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.model.ProfileType

class ProfileRemoteSnapshotPolicyTest {
    @Test
    fun normalizesDownloadOptionsWithoutRetainingAutoUpdate() {
        val normalized = ProfileRemoteSnapshotPolicy.normalizeRequest(
            RemoteProfileRequest(
                name = " Snapshot ",
                url = " https://example.com/config.yaml ",
                autoUpdate = true,
                userAgent = " Test Agent ",
                proxyUrl = " http://127.0.0.1:7890 ",
            ),
        )

        assertEquals("Snapshot", normalized.name)
        assertEquals("https://example.com/config.yaml", normalized.url)
        assertFalse(normalized.autoUpdate)
        assertEquals("Test Agent", normalized.userAgent)
        assertEquals("http://127.0.0.1:7890", normalized.proxyUrl)
    }

    @Test
    fun snapshotMetadataIsLocalAndDoesNotRetainRefreshSource() {
        val fields = ProfileRemoteSnapshotPolicy.catalogFields(
            id = "snapshot-id",
            name = "Snapshot",
            filePath = "/private/snapshot.yaml",
            createdAt = 10L,
            fileSize = 20L,
        )

        assertEquals(ProfileType.LOCAL.name, fields["type"])
        assertEquals(setOf("id", "name", "filePath", "createdAt", "isActive", "fileSize", "type"), fields.keys)
        assertFalse(fields.containsKey("url"))
        assertFalse(fields.containsKey("autoUpdate"))
    }
}
