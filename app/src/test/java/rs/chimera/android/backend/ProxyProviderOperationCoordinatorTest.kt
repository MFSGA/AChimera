package rs.chimera.android.backend

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyProviderOperationCoordinatorTest {
    @Test
    fun sameProviderOperationsAreSerialized() = runBlocking {
        val coordinator = ProxyProviderOperationCoordinator()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)

        val first = async(Dispatchers.Default) {
            coordinator.withLock("provider") {
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val second = async(Dispatchers.Default) {
            coordinator.withLock("provider") {
                secondEntered.countDown()
            }
        }
        assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS))

        releaseFirst.countDown()
        first.await()
        second.await()
        assertTrue(secondEntered.await(2, TimeUnit.SECONDS))
    }

    @Test
    fun cancelledWaiterDoesNotBlockLaterOperation() = runBlocking {
        val coordinator = ProxyProviderOperationCoordinator()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val cancelledEntered = CountDownLatch(1)
        val laterEntered = CountDownLatch(1)

        val first = async(Dispatchers.Default) {
            coordinator.withLock("provider") {
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val cancelled = async(Dispatchers.Default) {
            coordinator.withLock("provider") {
                cancelledEntered.countDown()
            }
        }
        assertFalse(cancelledEntered.await(100, TimeUnit.MILLISECONDS))
        cancelled.cancelAndJoin()

        releaseFirst.countDown()
        first.await()

        val later = async(Dispatchers.Default) {
            coordinator.withLock("provider") {
                laterEntered.countDown()
            }
        }
        later.await()
        assertTrue(laterEntered.await(2, TimeUnit.SECONDS))
        assertFalse(cancelledEntered.await(100, TimeUnit.MILLISECONDS))
    }

    @Test
    fun differentProvidersCanRunConcurrently() = runBlocking {
        val coordinator = ProxyProviderOperationCoordinator()
        val firstEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondEntered = CountDownLatch(1)

        val first = async(Dispatchers.Default) {
            coordinator.withLock("provider-a") {
                firstEntered.countDown()
                releaseFirst.await(2, TimeUnit.SECONDS)
            }
        }
        assertTrue(firstEntered.await(2, TimeUnit.SECONDS))

        val second = async(Dispatchers.Default) {
            coordinator.withLock("provider-b") {
                secondEntered.countDown()
            }
        }
        assertTrue(secondEntered.await(2, TimeUnit.SECONDS))

        releaseFirst.countDown()
        first.await()
        second.await()
    }
}
