package rs.chimera.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileCatalogSnapshotTest {
    @Test
    fun activeProfileDrivesSavedFilePath() {
        val snapshot = ProfileCatalogSnapshot.from(
            listOf(
                profile(id = "inactive", filePath = "/inactive.yaml", isActive = false),
                profile(id = "active", filePath = "/active.yaml", isActive = true),
            ),
        )

        assertEquals(listOf("inactive", "active"), snapshot.profiles.map { it.id })
        assertEquals("active", snapshot.activeProfile?.id)
        assertEquals("/active.yaml", snapshot.savedFilePath)
    }

    @Test
    fun missingActiveProfileLeavesSavedFilePathEmpty() {
        val snapshot = ProfileCatalogSnapshot.from(
            listOf(profile(id = "inactive", filePath = "/inactive.yaml", isActive = false)),
        )

        assertNull(snapshot.activeProfile)
        assertNull(snapshot.savedFilePath)
    }

    private fun profile(
        id: String,
        filePath: String,
        isActive: Boolean,
    ) = ProfileSummary(
        id = id,
        name = id,
        filePath = filePath,
        type = ProfileType.LOCAL,
        isActive = isActive,
        isRemote = false,
        lastUpdated = null,
        fileSize = 0L,
    )
}
