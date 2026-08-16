package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileActivePathPolicyTest {
    @Test
    fun nullActivePathRemovesPersistedSelection() {
        var removed = false
        var written: String? = null

        ProfileActivePathPolicy.persist(
            activePath = null,
            put = { written = it },
            remove = { removed = true },
        )

        assertTrue(removed)
        assertEquals(null, written)
    }

    @Test
    fun activePathWritesSelectionWithoutRemovingIt() {
        var removed = false
        var written: String? = null

        ProfileActivePathPolicy.persist(
            activePath = "/profiles/active.yaml",
            put = { written = it },
            remove = { removed = true },
        )

        assertFalse(removed)
        assertEquals("/profiles/active.yaml", written)
    }
}
