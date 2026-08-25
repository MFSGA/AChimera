package rs.chimera.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileVerificationPresentationTest {
    @Test
    fun missingResultMapsToFailureState() {
        val state = ProfileVerificationExecutionResult.Missing.toVerificationState(
            missingMessage = "missing",
            failureMessage = { "failure" },
        )

        assertEquals("missing", state.result)
        assertFalse(state.succeeded == true)
        assertFalse(state.isVerifying)
    }

    @Test
    fun verifiedResultMapsToSuccessState() {
        val state = ProfileVerificationExecutionResult.Verified("verified").toVerificationState(
            missingMessage = "missing",
            failureMessage = { "failure" },
        )

        assertEquals("verified", state.result)
        assertTrue(state.succeeded == true)
        assertFalse(state.isVerifying)
    }

    @Test
    fun failedResultUsesFailureFormatter() {
        val error = IllegalStateException("boom")
        val state = ProfileVerificationExecutionResult.Failed(error).toVerificationState(
            missingMessage = "missing",
            failureMessage = { failure -> "failure:${failure.message}" },
        )

        assertEquals("failure:boom", state.result)
        assertFalse(state.succeeded == true)
        assertFalse(state.isVerifying)
    }
}
