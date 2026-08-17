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
    fun `cookie and api credential headers are redacted`() {
        val sanitized = RuntimeLogSanitizer.sanitizeText(
            "Cookie: session=private-cookie\n" +
                "Set-Cookie: refresh=private-refresh; HttpOnly\n" +
                "X-Api-Key: private-api-key\n" +
                "X-Auth-Token: private-auth-token",
        )

        assertTrue(sanitized.contains("Cookie: ***"))
        assertTrue(sanitized.contains("Set-Cookie: ***"))
        assertTrue(sanitized.contains("X-Api-Key: ***"))
        assertTrue(sanitized.contains("X-Auth-Token: ***"))
        assertFalse(sanitized.contains("private-cookie"))
        assertFalse(sanitized.contains("private-refresh"))
        assertFalse(sanitized.contains("private-api-key"))
        assertFalse(sanitized.contains("private-auth-token"))
    }

    @Test
    fun `malformed urls still redact credentials and sensitive query`() {
        val sanitized = RuntimeLogSanitizer.sanitizeText(
            "download https://alice:secret@example.test/path?token=%ZZ#private-fragment",
        )

        assertTrue(sanitized.contains("https://***:***@example.test/path?token=***"))
        assertFalse(sanitized.contains("alice"))
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("private-fragment"))
        assertFalse(sanitized.contains("%ZZ"))
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

    @Test
    fun `session and oauth-style assignments are redacted`() {
        assertEquals(
            "refresh_token=*** client-secret: *** session=*** session_id='***' mode=direct",
            RuntimeLogSanitizer.sanitizeText(
                "refresh_token=refresh-secret client-secret: client-secret-value " +
                    "session=session-secret session_id='session-id-secret' mode=direct",
            ),
        )
    }

    @Test
    fun `session and oauth-style query keys are redacted`() {
        assertEquals(
            "https://example.com/profile?refresh_token=***&client_secret=***&session=***&session-id=***&safe=yes",
            RuntimeLogSanitizer.sanitizeText(
                "https://example.com/profile?refresh_token=refresh-secret&client_secret=client-secret-value&" +
                    "session=session-secret&session-id=session-id-secret&safe=yes",
            ),
        )
    }

    @Test
    fun `private app paths are redacted from runtime log text`() {
        val privateRoot = "/data/user/0/rs.chimera.android"

        assertEquals(
            "failed to open <app-private>/files/profiles/private.yaml",
            RuntimeLogSanitizer.sanitizePrivatePaths(
                value = "failed to open $privateRoot/files/profiles/private.yaml",
                privatePathPrefixes = listOf(privateRoot),
            ),
        )
    }
}
