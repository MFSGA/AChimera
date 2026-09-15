package rs.chimera.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserVisibleErrorTest {
    @Test
    fun `redacts credentials tokens and private paths`() {
        val result = sanitizeUserVisibleErrorText(
            value = "download https://alice:secret@example.com/p?token=abc failed at " +
                "/data/user/0/rs.chimera.android/cache/profile.yaml",
            fallback = "Unknown error",
            privatePathPrefixes = listOf("/data/user/0/rs.chimera.android"),
        )

        assertTrue(result.contains("https://***:***@example.com/p?token=***"))
        assertTrue(result.contains("<app-private>/cache/profile.yaml"))
        assertFalse(result.contains("alice"))
        assertFalse(result.contains("secret"))
        assertFalse(result.contains("token=abc"))
        assertFalse(result.contains("/data/user/0/rs.chimera.android"))
    }

    @Test
    fun `blank error uses fallback and multiline text becomes single line`() {
        assertEquals(
            "Unknown error",
            sanitizeUserVisibleErrorText("  ", "Unknown error"),
        )
        assertEquals(
            "first second third",
            sanitizeUserVisibleErrorText("first\nsecond\tthird", "Unknown error"),
        )
    }

    @Test
    fun `long messages are bounded`() {
        val result = sanitizeUserVisibleErrorText("x".repeat(500), "Unknown error")

        assertEquals(300, result.length)
        assertTrue(result.endsWith("…"))
    }
}
