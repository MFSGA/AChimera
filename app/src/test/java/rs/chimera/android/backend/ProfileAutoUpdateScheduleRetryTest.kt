package rs.chimera.android.backend

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
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

        val result = ProfileAutoUpdateScheduleRetry.replaceCurrent(
            loadPrevious = { Unit },
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

        val result = ProfileAutoUpdateScheduleRetry.replaceCurrent(
            loadPrevious = { Unit },
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

        val result = ProfileAutoUpdateScheduleRetry.replaceCurrent(
            loadPrevious = { Unit },
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

        val result = ProfileAutoUpdateScheduleRetry.replaceCurrent<Unit>(
            loadPrevious = { null },
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
    fun concurrentReplacementLoadsPreviousInsideSerializedSection() {
        val executor = Executors.newFixedThreadPool(2)
        val firstScheduling = CountDownLatch(1)
        val allowFirstScheduling = CountDownLatch(1)
        val loadedPrevious = mutableListOf<String?>()
        val stateLock = Any()
        var current: String? = "original"

        val first = executor.submit<Boolean> {
            ProfileAutoUpdateScheduleRetry.replaceCurrent(
                loadPrevious = {
                    synchronized(stateLock) { current.also(loadedPrevious::add) }
                },
                cancelPrevious = { synchronized(stateLock) { current = null } },
                scheduleReplacement = {
                    firstScheduling.countDown()
                    check(allowFirstScheduling.await(5, TimeUnit.SECONDS))
                    synchronized(stateLock) { current = "first" }
                    true
                },
                restorePrevious = {
                    synchronized(stateLock) { current = it }
                    true
                },
            )
        }
        assertTrue(firstScheduling.await(5, TimeUnit.SECONDS))

        val second = executor.submit<Boolean> {
            ProfileAutoUpdateScheduleRetry.replaceCurrent(
                loadPrevious = {
                    synchronized(stateLock) { current.also(loadedPrevious::add) }
                },
                cancelPrevious = { synchronized(stateLock) { current = null } },
                scheduleReplacement = { false },
                restorePrevious = {
                    synchronized(stateLock) { current = it }
                    true
                },
            )
        }

        allowFirstScheduling.countDown()
        assertTrue(first.get(5, TimeUnit.SECONDS))
        assertFalse(second.get(5, TimeUnit.SECONDS))
        executor.shutdownNow()

        assertEquals(listOf("original", "first"), loadedPrevious)
        assertEquals("first", synchronized(stateLock) { current })
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

        val result = ProfileAutoUpdateScheduleRetry.replaceCurrent(
            loadPrevious = { previous },
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
