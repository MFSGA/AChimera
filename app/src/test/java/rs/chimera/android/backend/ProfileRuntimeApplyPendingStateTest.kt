package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileRuntimeApplyPendingStateTest {
    @Test
    fun pendingProfileReturnsCurrentRevision() {
        val profile = profile(runtimeApplyPending = true, lastUpdated = 42L)

        assertEquals("profile" to 42L, pendingRuntimeApplyRevision(profile))
    }

    @Test
    fun nonPendingProfileHasNoRevisionToken() {
        assertNull(pendingRuntimeApplyRevision(profile(runtimeApplyPending = false, lastUpdated = 42L)))
        assertNull(pendingRuntimeApplyRevision(null))
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
