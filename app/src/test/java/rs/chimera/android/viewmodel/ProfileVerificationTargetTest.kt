package rs.chimera.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileVerificationTargetTest {
    @Test
    fun activeProfilePathWinsOverStaleSavedPath() {
        val target = resolveProfileVerificationTarget(
            profiles = listOf(profile(id = "new", path = "/profiles/new.yaml", active = true)),
            savedFilePath = "/profiles/old.yaml",
        )

        assertEquals("/profiles/new.yaml", target)
    }

    @Test
    fun savedPathIsUsedWhenCatalogHasNoActiveProfile() {
        val target = resolveProfileVerificationTarget(
            profiles = listOf(profile(id = "inactive", path = "/profiles/inactive.yaml", active = false)),
            savedFilePath = "/profiles/saved.yaml",
        )

        assertEquals("/profiles/saved.yaml", target)
    }

    private fun profile(
        id: String,
        path: String,
        active: Boolean,
    ) = ProfileSummary(
        id = id,
        name = id,
        filePath = path,
        type = ProfileType.LOCAL,
        isActive = active,
        isRemote = false,
        lastUpdated = null,
        fileSize = 0L,
    )
}
