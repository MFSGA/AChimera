package rs.chimera.android.service

import org.junit.Assert.assertEquals
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
}
