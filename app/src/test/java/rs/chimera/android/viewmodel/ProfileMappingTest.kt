package rs.chimera.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType as BackendProfileType
import rs.chimera.android.model.ProfileType

class ProfileMappingTest {
    @Test
    fun remoteProfilePreservesRemoteMetadata() {
        val profile =
            ProfileSummary(
                id = "remote-1",
                name = "Remote",
                filePath = "/profiles/remote.yaml",
                type = BackendProfileType.REMOTE,
                isActive = true,
                isRemote = true,
                lastUpdated = 1234L,
                fileSize = 5678L,
                url = "https://example.com/profile.yaml",
                autoUpdate = true,
                userAgent = "AChimera-Test",
                proxyUrl = "http://127.0.0.1:7890",
            ).toProfile()

        assertEquals(ProfileType.REMOTE, profile.type)
        assertTrue(profile.isActive)
        assertEquals("https://example.com/profile.yaml", profile.url)
        assertTrue(profile.autoUpdate)
        assertEquals("AChimera-Test", profile.userAgent)
        assertEquals("http://127.0.0.1:7890", profile.proxyUrl)
        assertEquals(1234L, profile.lastUpdated)
        assertEquals(5678L, profile.fileSize)
    }
}
