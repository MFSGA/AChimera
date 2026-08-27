package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileRuntimeApplyPendingStateTest {
    @Test
    fun pendingProfileReturnsCurrentRevisionAndSource() {
        val profile = profile(runtimeApplyPending = true, lastUpdated = 42L)

        assertEquals(
            ProfileRuntimeApplyPendingToken(
                profileId = "profile",
                profileRevision = 42L,
                sourceFingerprint = ProfileAutoUpdatePolicy.sourceFingerprint(profile),
            ),
            pendingRuntimeApplyToken(profile),
        )
    }

    @Test
    fun nonPendingProfileHasNoRevisionToken() {
        assertNull(pendingRuntimeApplyToken(profile(runtimeApplyPending = false, lastUpdated = 42L)))
        assertNull(pendingRuntimeApplyToken(null))
    }

    private fun profile(
        runtimeApplyPending: Boolean,
        lastUpdated: Long?,
    ) = ProfileSummary(
        id = "profile",
        name = "Profile",
        filePath = "/tmp/profile.yaml",
        type = ProfileType.REMOTE,
        isActive = true,
        isRemote = true,
        lastUpdated = lastUpdated,
        fileSize = 1L,
        runtimeApplyPending = runtimeApplyPending,
    )
}
