package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileProviderResultPolicyTest {
    @Test
    fun keepsProviderNameWhenPresent() {
        assertEquals("Imported profile", profileProviderResultName("Imported profile", "New profile"))
    }

    @Test
    fun fallsBackWhenProviderNameIsMissingOrBlank() {
        assertEquals("New profile", profileProviderResultName(null, "New profile"))
        assertEquals("New profile", profileProviderResultName("  ", "New profile"))
    }

    @Test
    fun acceptsHttpProfileUrls() {
        assertEquals(
            "https://example.com/profile.yaml",
            profileProviderResultUrl("https://example.com/profile.yaml"),
        )
    }

    @Test
    fun rejectsNonHttpProviderUris() {
        assertNull(profileProviderResultUrl("content://provider/profile.yaml"))
        assertNull(profileProviderResultUrl("file:///profiles/profile.yaml"))
    }
}
