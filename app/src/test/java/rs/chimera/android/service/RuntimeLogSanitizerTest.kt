package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogSanitizerTest {
    @Test
    fun `profile label keeps only the file name`() {
        assertEquals(
            "private-profile.yaml",
            RuntimeLogSanitizer.profileLabel(
                "/data/user/0/rs.chimera.android/files/accounts/alice/private-profile.yaml",
            ),
        )
    }

    @Test
    fun `profile label falls back when path has no file name`() {
        assertEquals("unknown-profile", RuntimeLogSanitizer.profileLabel(""))
    }

    @Test
    fun `url user info and sensitive query values are redacted`() {
        val sanitized = RuntimeLogSanitizer.sanitizeText(
            "download https://alice:secret@example.com/profile?token=abc123&name=demo#oauth",
        )

        assertEquals(
            "download https://***:***@example.com/profile?token=***&name=demo#***",
            sanitized,
        )
    }

    @Test
    fun `proxy credentials and authorization headers are redacted`() {
        val sanitized = RuntimeLogSanitizer.sanitizeText(
            "proxy=http://user:pass@127.0.0.1:7890\n" +
                "Authorization: Bearer top-secret\n" +
                "Proxy-Authorization: Basic c2VjcmV0",
        )

        assertTrue(sanitized.contains("http://***:***@127.0.0.1:7890"))
        assertTrue(sanitized.contains("Authorization: ***"))
        assertTrue(sanitized.contains("Proxy-Authorization: ***"))
        assertFalse(sanitized.contains("top-secret"))
        assertFalse(sanitized.contains("c2VjcmV0"))
    }

    @Test
    fun `inline credentials are redacted without changing ordinary values`() {
        assertEquals(
            "token=*** password: *** mode=direct api_key='***'",
            RuntimeLogSanitizer.sanitizeText(
                "token=abc password: hunter2 mode=direct api_key='private-key'",
            ),
        )
    }

    @Test
    fun `encoded sensitive query keys are redacted`() {
        assertEquals(
            "https://example.com/profile?access%5Ftoken=***&sig=***&safe=yes",
            RuntimeLogSanitizer.sanitizeText(
                "https://example.com/profile?access%5Ftoken=abc&sig=xyz&safe=yes",
            ),
        )
    }
}
