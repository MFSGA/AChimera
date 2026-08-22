package rs.chimera.android.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType as BackendProfileType
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType

class ProfileMutationGuardTest {
    @Test
    fun matchingRemoteProfileCanProceed() {
        val profile = remoteProfile()

        assertTrue(profile.matchesMutationSnapshot(profile.toSummary()))
    }

    @Test
    fun changedRevisionBlocksStaleMutation() {
        val profile = remoteProfile()

        assertFalse(
            profile.matchesMutationSnapshot(
                profile.toSummary().copy(lastUpdated = requireNotNull(profile.lastUpdated) + 1),
            ),
        )
    }

    @Test
    fun changedRemoteSettingsBlockStaleMutation() {
        val profile = remoteProfile()

        assertFalse(
            profile.matchesMutationSnapshot(
                profile.toSummary().copy(url = "https://example.com/new.yaml"),
            ),
        )
    }

    private fun remoteProfile() =
        Profile(
            id = "profile-id",
            name = "Remote",
            filePath = "/tmp/profile.yaml",
            type = ProfileType.REMOTE,
            url = "https://example.com/profile.yaml",
            lastUpdated = 42L,
            autoUpdate = true,
            userAgent = "AChimera",
            proxyUrl = "http://127.0.0.1:7890",
        )

    private fun Profile.toSummary() =
        ProfileSummary(
            id = id,
            name = name,
            filePath = filePath,
            type = BackendProfileType.REMOTE,
            isActive = isActive,
            isRemote = true,
            lastUpdated = lastUpdated,
            fileSize = fileSize,
            url = url,
            autoUpdate = autoUpdate,
            userAgent = userAgent,
            proxyUrl = proxyUrl,
        )
}
