package rs.chimera.android.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAutoUpdateScheduleRetryTest {
    @Test
    fun firstSuccessDoesNotRetry() {
        var attempts = 0

        val result = ProfileAutoUpdateScheduleRetry.run {
            attempts += 1
            true
        }

        assertTrue(result)
        assertTrue(attempts == 1)
    }

    @Test
    fun secondAttemptCanRecoverScheduling() {
        var attempts = 0

        val result = ProfileAutoUpdateScheduleRetry.run {
            attempts += 1
            attempts == 2
        }

        assertTrue(result)
        assertTrue(attempts == 2)
    }

    @Test
    fun twoFailuresRemainFailure() {
        var attempts = 0

        val result = ProfileAutoUpdateScheduleRetry.run {
            attempts += 1
            false
        }

        assertFalse(result)
        assertTrue(attempts == 2)
    }
}
