package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileMutationSnapshotTest {
    @Test
    fun snapshotMatchesUnchangedProfile() {
        val profile = remoteProfile()

        assertTrue(ProfileMutationSnapshot.from(profile).matches(profile))
    }

    @Test
    fun snapshotRejectsChangedRevision() {
        val profile = remoteProfile()
        val snapshot = ProfileMutationSnapshot.from(profile)

        assertFalse(snapshot.matches(profile.copy(lastUpdated = 101L)))
    }

    @Test
    fun snapshotRejectsChangedRemoteSettings() {
        val profile = remoteProfile()
        val snapshot = ProfileMutationSnapshot.from(profile)

        assertFalse(snapshot.matches(profile.copy(url = "https://new.example/profile.yaml")))
    }

    @Test
    fun snapshotRejectsChangedName() {
        val profile = remoteProfile()
        val snapshot = ProfileMutationSnapshot.from(profile)

        assertFalse(snapshot.matches(profile.copy(name = "renamed")))
    }

    private fun remoteProfile() = ProfileSummary(
        id = "profile-1",
        name = "remote",
        filePath = "/tmp/profile.yaml",
        type = ProfileType.REMOTE,
        isActive = false,
        isRemote = true,
        lastUpdated = 100L,
        fileSize = 42L,
        url = "https://example.com/profile.yaml",
        autoUpdate = true,
        userAgent = "AChimera",
        proxyUrl = "",
    )
}
