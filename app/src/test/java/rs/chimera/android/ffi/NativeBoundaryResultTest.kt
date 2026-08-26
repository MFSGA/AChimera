package rs.chimera.android.ffi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBoundaryResultTest {
    @Test
    fun missingNativeSymbolRemainsDiagnosticFailure() {
        val result = runCatchingNativeBoundary<Int> {
            throw UnsatisfiedLinkError("missing")
        }

        assertTrue(result.isFailure)
        assertEquals(UnsatisfiedLinkError::class.java, result.exceptionOrNull()?.javaClass)
    }

    @Test
    fun ordinaryFailureRemainsDiagnosticFailure() {
        val result = runCatchingNativeBoundary<Int> {
            throw IllegalStateException("failed")
        }

        assertTrue(result.isFailure)
        assertEquals(IllegalStateException::class.java, result.exceptionOrNull()?.javaClass)
    }

    @Test
    fun fatalErrorPropagates() {
        val error = runCatching {
            runCatchingNativeBoundary<Int> {
                throw AssertionError("fatal")
            }
        }.exceptionOrNull()

        assertTrue(error is AssertionError)
    }
}
