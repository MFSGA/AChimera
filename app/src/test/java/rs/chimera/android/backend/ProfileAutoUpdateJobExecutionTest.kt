package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileAutoUpdateJobExecutionTest {
    @Test
    fun cancellationPropagatesInsteadOfCompletingJob() = runBlocking {
        val error = runCatching {
            runProfileAutoUpdateJob {
                throw CancellationException("job stopped")
            }
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }

    @Test
    fun ordinaryFailureRequestsRetryThroughResult() = runBlocking {
        val result = runProfileAutoUpdateJob {
            throw IllegalStateException("catalog unavailable")
        }

        assertTrue(result.isFailure)
        assertEquals(IllegalStateException::class.java, result.exceptionOrNull()?.javaClass)
    }
}
