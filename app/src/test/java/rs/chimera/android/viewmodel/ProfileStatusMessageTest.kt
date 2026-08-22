package rs.chimera.android.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileStatusMessageTest {
    @Test
    fun clearingMessageAlsoClearsErrorSeverity() {
        assertFalse(ProfileStatusMessage.of(null, isError = true).isError)
    }

    @Test
    fun nonEmptyErrorMessageKeepsErrorSeverity() {
        assertTrue(ProfileStatusMessage.of("failed", isError = true).isError)
    }
}
