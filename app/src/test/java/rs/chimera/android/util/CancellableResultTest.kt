package rs.chimera.android.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableResultTest {
    @Test
    fun recoverableFailureRemainsInResult() {
        val result = runCatchingRecoverable<Int> {
            throw IllegalStateException("failed")
        }

        assertTrue(result.isFailure)
        assertEquals(IllegalStateException::class.java, result.exceptionOrNull()?.javaClass)
    }

    @Test
    fun recoverableFatalErrorPropagates() {
        val error = runCatching {
            runCatchingRecoverable<Int> {
                throw AssertionError("fatal")
            }
        }.exceptionOrNull()

        assertTrue(error is AssertionError)
    }

    @Test
    fun cancellationPropagates() = runBlocking {
        val error = runCatching {
            runCatchingPreservingCancellation<Int> {
                throw CancellationException("stopped")
            }
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }

    @Test
    fun ordinaryFailureRemainsInResult() = runBlocking {
        val result = runCatchingPreservingCancellation<Int> {
            throw IllegalStateException("failed")
        }

        assertTrue(result.isFailure)
        assertEquals(IllegalStateException::class.java, result.exceptionOrNull()?.javaClass)
    }

    @Test
    fun fatalErrorPropagates() = runBlocking {
        val error = runCatching {
            runCatchingPreservingCancellation<Int> {
                throw AssertionError("fatal")
            }
        }.exceptionOrNull()

        assertTrue(error is AssertionError)
    }
}
