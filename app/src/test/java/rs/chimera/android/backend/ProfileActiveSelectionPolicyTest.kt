package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileActiveSelectionPolicyTest {
    @Test
    fun savedPathWinsOverStaleCatalogFlag() {
        assertEquals(
            "/profiles/b.yaml",
            ProfileActiveSelectionPolicy.resolveActivePath(
                entries = listOf(
                    profile("a", "/profiles/a.yaml", active = true),
                    profile("b", "/profiles/b.yaml", active = false),
                ),
                savedPath = "/profiles/b.yaml",
            ),
        )
    }

    @Test
    fun staleSavedPathFallsBackToCatalogActiveProfile() {
        assertEquals(
            "/profiles/a.yaml",
            ProfileActiveSelectionPolicy.resolveActivePath(
                entries = listOf(profile("a", "/profiles/a.yaml", active = true)),
                savedPath = "/profiles/missing.yaml",
            ),
        )
    }

    @Test
    fun multipleCatalogFlagsResolveDeterministically() {
        assertEquals(
            "/profiles/a.yaml",
            ProfileActiveSelectionPolicy.resolveActivePath(
                entries = listOf(
                    profile("a", "/profiles/a.yaml", active = true),
                    profile("b", "/profiles/b.yaml", active = true),
                ),
                savedPath = null,
            ),
        )
    }

    @Test
    fun noSelectionReturnsNull() {
        assertNull(
            ProfileActiveSelectionPolicy.resolveActivePath(
                entries = listOf(profile("a", "/profiles/a.yaml", active = false)),
                savedPath = null,
            ),
        )
    }

    private fun profile(id: String, path: String, active: Boolean) =
        ProfileCatalogEntry(id = id, name = id, filePath = path, isActive = active)
}
