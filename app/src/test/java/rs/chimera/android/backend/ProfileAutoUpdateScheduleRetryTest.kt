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

    @Test
    fun replacementFailureRestoresPreviousJob() {
        var cancelled = false
        var replacementAttempts = 0
        var restoreAttempts = 0

        val result = ProfileAutoUpdateScheduleRetry.replace(
            previous = Unit,
            cancelPrevious = { cancelled = true },
            scheduleReplacement = {
                replacementAttempts += 1
                false
            },
            restorePrevious = {
                restoreAttempts += 1
                true
            },
        )

        assertFalse(result)
        assertTrue(cancelled)
        assertTrue(replacementAttempts == 2)
        assertTrue(restoreAttempts == 1)
    }

    @Test
    fun secondRestoreAttemptCanRecoverPreviousJob() {
        var restoreAttempts = 0

        val result = ProfileAutoUpdateScheduleRetry.replace(
            previous = Unit,
            cancelPrevious = {},
            scheduleReplacement = { false },
            restorePrevious = {
                restoreAttempts += 1
                restoreAttempts == 2
            },
        )

        assertFalse(result)
        assertTrue(restoreAttempts == 2)
    }

    @Test
    fun successfulReplacementDoesNotRestorePreviousJob() {
        var restoreAttempts = 0

        val result = ProfileAutoUpdateScheduleRetry.replace(
            previous = Unit,
            cancelPrevious = {},
            scheduleReplacement = { true },
            restorePrevious = {
                restoreAttempts += 1
                true
            },
        )

        assertTrue(result)
        assertTrue(restoreAttempts == 0)
    }

    @Test
    fun replacementFailureWithoutPreviousJobDoesNotRestore() {
        var restoreAttempts = 0

        val result = ProfileAutoUpdateScheduleRetry.replace<Unit>(
            previous = null,
            cancelPrevious = {},
            scheduleReplacement = { false },
            restorePrevious = {
                restoreAttempts += 1
                true
            },
        )

        assertFalse(result)
        assertTrue(restoreAttempts == 0)
    }

    @Test
    fun restoreReceivesExactPreviousJobConfiguration() {
        val previous = PreviousJobConfiguration(
            extras = mapOf("marker" to "keep"),
            networkType = 1,
            backoffMillis = 30_000L,
            persisted = false,
        )
        var restored: PreviousJobConfiguration? = null

        val result = ProfileAutoUpdateScheduleRetry.replace(
            previous = previous,
            cancelPrevious = {},
            scheduleReplacement = { false },
            restorePrevious = {
                restored = it
                true
            },
        )

        assertFalse(result)
        assertTrue(restored === previous)
        assertTrue(restored == previous)
    }

    private data class PreviousJobConfiguration(
        val extras: Map<String, String>,
        val networkType: Int,
        val backoffMillis: Long,
        val persisted: Boolean,
    )
}
