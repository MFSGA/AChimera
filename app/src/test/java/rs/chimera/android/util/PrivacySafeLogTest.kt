package rs.chimera.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrivacySafeLogTest {
    @Test
    fun releaseMessageKeepsOnlyFixedContextAndExceptionType() {
        val releaseMessage = PrivacySafeLog.releaseMessage(
            message = "Failed to update profile",
            error = IllegalStateException(
                "https://alice:secret@example.com/profile?token=private-token",
            ),
        )

        assertEquals(
            "Failed to update profile (IllegalStateException)",
            releaseMessage,
        )
        assertFalse(releaseMessage.contains("example.com"))
        assertFalse(releaseMessage.contains("private-token"))
    }

    @Test
    fun releaseCrashThrowablePreservesStackWithoutSensitiveMessageOrCause() {
        val original = IllegalArgumentException("token=private-token").apply {
            stackTrace = arrayOf(StackTraceElement("Example", "run", "Example.kt", 42))
        }

        val sanitized = PrivacySafeLog.releaseCrashThrowable(original)

        assertEquals("IllegalArgumentException", sanitized.message)
        assertEquals(original.stackTrace.toList(), sanitized.stackTrace.toList())
        assertEquals(null, sanitized.cause)
        assertFalse(sanitized.toString().contains("private-token"))
    }
}
