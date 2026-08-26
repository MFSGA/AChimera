package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileActiveProfileRefreshTest {
    @Test
    fun recoverableLoadFailurePreservesCurrentProfile() {
        val current = profile("current")

        val refreshed = refreshActiveProfileValue(current) {
            error("catalog temporarily unavailable")
        }

        assertEquals(current, refreshed)
    }

    @Test
    fun successfulEmptyLoadClearsCurrentProfile() {
        assertNull(refreshActiveProfileValue(profile("current")) { null })
    }

    @Test(expected = AssertionError::class)
    fun fatalLoadErrorPropagates() {
        refreshActiveProfileValue(profile("current")) {
            throw AssertionError("fatal")
        }
    }

    private fun profile(id: String) = ProfileSummary(
        id = id,
        name = id,
        filePath = "/tmp/$id.yaml",
        type = ProfileType.LOCAL,
        isActive = true,
        isRemote = false,
        lastUpdated = 1L,
        fileSize = 1L,
    )
}
