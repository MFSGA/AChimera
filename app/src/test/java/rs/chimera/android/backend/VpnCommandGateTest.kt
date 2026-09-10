package rs.chimera.android.backend

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class VpnCommandGateTest {
    @Test
    fun concurrentStartsDispatchOnlyOnce() = runBlocking {
        var state = ServiceState.STOPPED
        val gate = VpnCommandGate { state }
        val release = CompletableDeferred<Unit>()
        var starts = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.start {
                starts++
                release.await()
                state = ServiceState.STARTING
            }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) { gate.start { starts++ } }
        release.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, starts)
    }

    @Test
    fun startCannotInterruptStopping() = runBlocking {
        val gate = VpnCommandGate { ServiceState.STOPPING }
        val result = runCatching { gate.start { error("must not dispatch") } }
        assertEquals("VPN service is stopping", result.exceptionOrNull()?.message)
    }

    @Test
    fun cancelledCommandReleasesGateForStop() = runBlocking {
        var state = ServiceState.STOPPED
        val gate = VpnCommandGate { state }
        val start = async(start = CoroutineStart.UNDISPATCHED) {
            gate.start { CompletableDeferred<Unit>().await() }
        }
        start.cancel()
        start.join()
        state = ServiceState.RUNNING
        var stopped = false
        gate.stop { shouldStopRuntime -> stopped = shouldStopRuntime }
        assertTrue(stopped)
    }

    @Test
    fun repeatedStopDoesNotDispatchRuntimeShutdown() = runBlocking {
        val gate = VpnCommandGate { ServiceState.STOPPED }
        var shouldStopRuntime = true

        gate.stop { shouldStopRuntime = it }

        assertEquals(false, shouldStopRuntime)
    }

    @Test
    fun concurrentStopsDispatchRuntimeShutdownOnlyOnce() = runBlocking {
        var state = ServiceState.RUNNING
        val gate = VpnCommandGate { state }
        val release = CompletableDeferred<Unit>()
        var stops = 0
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            gate.stop { shouldStopRuntime ->
                if (shouldStopRuntime) {
                    stops++
                    state = ServiceState.STOPPING
                    release.await()
                }
            }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            gate.stop { shouldStopRuntime ->
                if (shouldStopRuntime) stops++
            }
        }

        release.complete(Unit)
        first.await()
        second.await()

        assertEquals(1, stops)
    }
}
