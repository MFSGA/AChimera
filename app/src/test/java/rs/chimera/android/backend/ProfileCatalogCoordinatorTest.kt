package rs.chimera.android.backend

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCatalogCoordinatorTest {
    @Test
    fun serializesCatalogMutations() {
        val coordinator = ProfileCatalogCoordinator()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondAttempted = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)

        val first = thread {
            coordinator.withLock {
                firstEntered.countDown()
                releaseFirst.await()
            }
        }
        assertTrue(firstEntered.await(1, TimeUnit.SECONDS))

        val second = thread {
            secondAttempted.countDown()
            coordinator.withLock {
                secondEntered.countDown()
            }
        }
        assertTrue(secondAttempted.await(1, TimeUnit.SECONDS))
        assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))

        releaseFirst.countDown()
        assertTrue(secondEntered.await(1, TimeUnit.SECONDS))
        first.join()
        second.join()
    }

    @Test
    fun releasesCatalogLockAfterFailure() {
        val coordinator = ProfileCatalogCoordinator()

        runCatching {
            coordinator.withLock { error("boom") }
        }

        var entered = false
        coordinator.withLock { entered = true }

        assertTrue(entered)
    }
}
