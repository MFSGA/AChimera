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

    @Test
    fun importedProfileSurvivesRecoverableRefreshFailure() {
        val committed = profile("imported").copy(isActive = false)

        val refreshed = refreshImportedActiveProfileValue(committed) {
            error("catalog temporarily unavailable")
        }

        assertEquals(committed.copy(isActive = true), refreshed)
    }

    @Test
    fun importedProfileUsesCanonicalRefreshWhenAvailable() {
        val committed = profile("imported").copy(isActive = false)
        val canonical = profile("imported").copy(name = "canonical")

        val refreshed = refreshImportedActiveProfileValue(committed) { canonical }

        assertEquals(canonical, refreshed)
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
