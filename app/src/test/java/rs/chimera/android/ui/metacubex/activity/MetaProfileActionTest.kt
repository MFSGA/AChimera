package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class MetaProfileActionTest {
    @Test
    fun verifyRequiresActiveProfile() {
        assertFalse(MetaProfileAction.Verify.isAvailableFor(profile()))
        assertTrue(MetaProfileAction.Verify.isAvailableFor(profile(isActive = true)))
    }

    @Test
    fun remoteActionsRequireRemoteProfile() {
        assertFalse(MetaProfileAction.Update.isAvailableFor(profile()))
        assertFalse(MetaProfileAction.EditRemoteSettings.isAvailableFor(profile()))
        assertTrue(MetaProfileAction.Update.isAvailableFor(profile(isRemote = true)))
        assertTrue(MetaProfileAction.EditRemoteSettings.isAvailableFor(profile(isRemote = true)))
    }

    @Test
    fun commonActionsRemainAvailable() {
        val profile = profile()

        assertTrue(MetaProfileAction.Details.isAvailableFor(profile))
        assertTrue(MetaProfileAction.Rename.isAvailableFor(profile))
        assertTrue(MetaProfileAction.Delete.isAvailableFor(profile))
    }

    private fun profile(
        isActive: Boolean = false,
        isRemote: Boolean = false,
    ) = ProfileSummary(
        id = "profile-1",
        name = "profile",
        filePath = "/tmp/profile.yaml",
        type = if (isRemote) ProfileType.REMOTE else ProfileType.LOCAL,
        isActive = isActive,
        isRemote = isRemote,
        lastUpdated = 100L,
        fileSize = 42L,
        url = if (isRemote) "https://example.com/profile.yaml" else null,
        autoUpdate = false,
        userAgent = null,
        proxyUrl = null,
    )
}
