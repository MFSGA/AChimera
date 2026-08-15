package rs.chimera.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileAutoUpdatePresentationTest {
    @Test
    fun disabledAutoUpdateHasNoPresentation() {
        assertNull(
            resolveProfileAutoUpdatePresentation(
                autoUpdate = false,
                lastAttempt = 1_000,
                failureCount = 2,
                nextAttemptAt = 2_000,
                error = "failed",
            ),
        )
    }

    @Test
    fun enabledProfileStartsWaitingThenShowsSuccess() {
        val waiting = resolveProfileAutoUpdatePresentation(
            autoUpdate = true,
            lastAttempt = null,
            failureCount = 0,
            nextAttemptAt = null,
            error = null,
        )
        val success = resolveProfileAutoUpdatePresentation(
            autoUpdate = true,
            lastAttempt = 1_000,
            failureCount = 0,
            nextAttemptAt = null,
            error = null,
        )

        assertEquals(ProfileAutoUpdateStatus.WAITING, waiting?.status)
        assertEquals(ProfileAutoUpdateStatus.SUCCESS, success?.status)
    }

    @Test
    fun failureTakesPriorityAndNormalizesInputs() {
        val retry = resolveProfileAutoUpdatePresentation(
            autoUpdate = true,
            lastAttempt = 1_000,
            failureCount = 3,
            nextAttemptAt = 2_000,
            error = "  IllegalStateException  ",
        )

        assertEquals(ProfileAutoUpdateStatus.RETRY, retry?.status)
        assertEquals(3, retry?.failureCount)
        assertEquals(2_000L, retry?.nextAttemptAt)
        assertEquals("IllegalStateException", retry?.error)
    }
}
