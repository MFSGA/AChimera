package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class ProfileStartupRecoveryTest {
    @Test
    fun recoverableExceptionReturnsFailure() {
        val expected = IllegalStateException("recoverable")

        val result = runProfileStartupRecoveryStep { throw expected }

        assertSame(expected, result.exceptionOrNull())
    }

    @Test
    fun fatalErrorPropagates() {
        val expected = AssertionError("fatal")

        try {
            runProfileStartupRecoveryStep { throw expected }
            fail("Expected fatal error to propagate")
        } catch (error: AssertionError) {
            assertSame(expected, error)
        }
    }

    @Test
    fun successfulStepReturnsUnit() {
        assertEquals(Unit, runProfileStartupRecoveryStep {}.getOrThrow())
    }
}
