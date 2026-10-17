package rs.chimera.android.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnCleanupContextTest {
    @Test
    fun cleanupCanAcquireRuntimeLockAfterCallerCancellation() = runBlocking {
        val runtimeMutex = Mutex(locked = true)
        val cleanupWaitingForLock = CompletableDeferred<Unit>()
        var cleanupCompleted = false

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                withVpnCleanupContext {
                    cleanupWaitingForLock.complete(Unit)
                    runtimeMutex.withLock { cleanupCompleted = true }
                }
            }
        }

        job.cancel()
        cleanupWaitingForLock.await()
        runtimeMutex.unlock()
        job.join()

        assertTrue(cleanupCompleted)
    }
}
