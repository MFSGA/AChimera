package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertEquals
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
        assertFalse(MetaProfileAction.ShareQrCode.isAvailableFor(profile()))
        assertTrue(MetaProfileAction.Update.isAvailableFor(profile(isRemote = true)))
        assertTrue(MetaProfileAction.Edit.isAvailableFor(profile()))
        assertTrue(MetaProfileAction.Edit.isAvailableFor(profile(isRemote = true)))
        assertTrue(MetaProfileAction.ShareQrCode.isAvailableFor(profile(isRemote = true)))
        assertFalse(MetaProfileAction.ShareQrCode.isAvailableFor(profile(isRemote = true, url = " ")))
    }

    @Test
    fun commonActionsRemainAvailable() {
        val profile = profile()

        assertTrue(MetaProfileAction.Details.isAvailableFor(profile))
        assertTrue(MetaProfileAction.Edit.isAvailableFor(profile))
        assertTrue(MetaProfileAction.Delete.isAvailableFor(profile))
        assertTrue(MetaProfileAction.Duplicate.isAvailableFor(profile))
        assertTrue(MetaProfileAction.Duplicate.isAvailableFor(profile(isRemote = true)))
    }

    @Test
    fun blockedActionKeepsReferenceAppearanceAndExplainsWhyItIsUnavailable() {
        val presentation = metaProfileActionPresentation("Edit", "Stop VPN") { action, reason ->
            "$action ($reason)"
        }

        assertTrue(presentation.isEnabled)
        assertEquals(1f, presentation.alpha)
        assertEquals("Edit (Stop VPN)", presentation.contentDescription)
    }

    @Test
    fun availableActionKeepsItsNormalDescription() {
        val presentation = metaProfileActionPresentation("Edit", null) { action, reason ->
            "$action ($reason)"
        }

        assertTrue(presentation.isEnabled)
        assertEquals(1f, presentation.alpha)
        assertEquals("Edit", presentation.contentDescription)
    }

    @Test
    fun cmfaMenuContainsOnlyReferenceActionsInReferenceOrder() {
        assertEquals(
            listOf(
                MetaProfileAction.Edit,
                MetaProfileAction.Duplicate,
                MetaProfileAction.Delete,
            ),
            cmfaProfileMenuActions(profile()),
        )
        assertEquals(
            listOf(
                MetaProfileAction.Update,
                MetaProfileAction.Edit,
                MetaProfileAction.Duplicate,
                MetaProfileAction.ShareQrCode,
                MetaProfileAction.Delete,
            ),
            cmfaProfileMenuActions(profile(isRemote = true)),
        )
        assertEquals(
            listOf(
                MetaProfileAction.Update,
                MetaProfileAction.Edit,
                MetaProfileAction.Duplicate,
                MetaProfileAction.ShareQrCode,
                MetaProfileAction.Delete,
            ),
            cmfaProfileMenuActions(profile(isRemote = true, isActive = true)),
        )
    }

    private fun profile(
        isActive: Boolean = false,
        isRemote: Boolean = false,
        url: String? = if (isRemote) "https://example.com/profile.yaml" else null,
    ) = ProfileSummary(
        id = "profile-1",
        name = "profile",
        filePath = "/tmp/profile.yaml",
        type = if (isRemote) ProfileType.REMOTE else ProfileType.LOCAL,
        isActive = isActive,
        isRemote = isRemote,
        lastUpdated = 100L,
        fileSize = 42L,
        url = url,
        autoUpdate = false,
        userAgent = null,
        proxyUrl = null,
    )
}
