package rs.chimera.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileVerificationStateTest {
    @Test
    fun verifyingClearsPreviousResultAndOutcome() {
        val state = ProfileVerificationState.verifying()

        assertTrue(state.isVerifying)
        assertNull(state.result)
        assertNull(state.succeeded)
    }

    @Test
    fun completedPublishesResultAndOutcomeAtomically() {
        val state = ProfileVerificationState.completed(
            result = "verified",
            succeeded = true,
        )

        assertFalse(state.isVerifying)
        assertEquals("verified", state.result)
        assertEquals(true, state.succeeded)
    }
}
