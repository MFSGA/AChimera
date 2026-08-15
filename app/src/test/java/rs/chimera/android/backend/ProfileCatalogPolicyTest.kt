package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCatalogPolicyTest {
    @Test
    fun activateMakesOnlyRequestedProfileActive() {
        val result = ProfileCatalogPolicy.activate(profiles(), "second")!!

        assertEquals(listOf(false, true, false), result.map { it.isActive })
        assertEquals("/profiles/second.yaml", ProfileCatalogPolicy.activePath(result))
    }

    @Test
    fun activateUnknownProfileDoesNotMutateCatalog() {
        assertNull(ProfileCatalogPolicy.activate(profiles(), "missing"))
    }

    @Test
    fun deletingActiveProfileActivatesFirstRemainingProfile() {
        val result = ProfileCatalogPolicy.delete(profiles(), "first")!!

        assertEquals(listOf("second", "shared"), result.profiles.map { it.id })
        assertTrue(result.profiles.first().isActive)
        assertEquals("/profiles/second.yaml", ProfileCatalogPolicy.activePath(result.profiles))
        assertTrue(result.shouldDeleteFile)
    }

    @Test
    fun deletingProfileFromCatalogWithoutActiveEntryRecoversFirstRemainingProfile() {
        val input = listOf(
            profile("first", "/profiles/first.yaml"),
            profile("second", "/profiles/second.yaml"),
        )

        val result = ProfileCatalogPolicy.delete(input, "second")!!

        assertEquals(listOf("first"), result.profiles.map { it.id })
        assertTrue(result.profiles.single().isActive)
        assertEquals("/profiles/first.yaml", ProfileCatalogPolicy.activePath(result.profiles))
    }

    @Test
    fun deletingProfileKeepsFileReferencedByAnotherProfile() {
        val input = profiles() + profile("duplicate", "/profiles/shared.yaml")
        val result = ProfileCatalogPolicy.delete(input, "shared")!!

        assertFalse(result.shouldDeleteFile)
    }

    @Test
    fun activePathReturnsNullWhenCatalogHasNoActiveEntry() {
        val input = listOf(
            profile("first", "/profiles/first.yaml"),
            profile("second", "/profiles/second.yaml"),
        )

        assertNull(ProfileCatalogPolicy.activePath(input))
    }

    @Test
    fun renamePreservesPathAndActiveSelection() {
        val result = ProfileCatalogPolicy.rename(profiles(), "first", "Renamed")!!
        val renamed = result.first()

        assertEquals("Renamed", renamed.name)
        assertEquals("/profiles/first.yaml", renamed.filePath)
        assertTrue(renamed.isActive)
    }

    private fun profiles() =
        listOf(
            profile("first", "/profiles/first.yaml", isActive = true),
            profile("second", "/profiles/second.yaml"),
            profile("shared", "/profiles/shared.yaml"),
        )

    private fun profile(
        id: String,
        path: String,
        isActive: Boolean = false,
    ) = ProfileCatalogEntry(id, id, path, isActive)
}
