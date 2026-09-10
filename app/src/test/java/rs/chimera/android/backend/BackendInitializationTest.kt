package rs.chimera.android.backend

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BackendInitializationTest {
    @Test
    fun cancelledCallerDoesNotCancelSharedRecovery() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val initialization = BackendInitialization(scope) {
            calls.incrementAndGet()
            entered.complete(Unit)
            release.await()
        }
        try {
            assertEquals(0, calls.get())
            val first = async(start = CoroutineStart.UNDISPATCHED) { initialization.awaitReady() }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { initialization.awaitReady() }
            first.cancel()
            assertFalse(second.isCompleted)
            release.complete(Unit)
            second.await()
            initialization.awaitReady()
            assertEquals(1, calls.get())
        } finally {
            scope.cancel()
        }
    }
}
