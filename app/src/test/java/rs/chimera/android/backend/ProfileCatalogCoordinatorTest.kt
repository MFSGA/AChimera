package rs.chimera.android.backend

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
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
    fun snapshotReadDoesNotInterleaveCatalogMutation() {
        val coordinator = ProfileCatalogCoordinator()
        val documentRead = CountDownLatch(1)
        val releaseDocumentRead = CountDownLatch(1)
        val mutationAttempted = CountDownLatch(1)
        var activePath = "old"
        var snapshot: Pair<String, String?>? = null

        val reader = thread {
            snapshot = readConsistentProfileCatalogSnapshot(
                coordinator = coordinator,
                readDocument = {
                    documentRead.countDown()
                    releaseDocumentRead.await()
                    "catalog"
                },
                readActivePath = { activePath },
            )
        }
        assertTrue(documentRead.await(1, TimeUnit.SECONDS))

        val mutation = thread {
            mutationAttempted.countDown()
            coordinator.withLock { activePath = "new" }
        }
        assertTrue(mutationAttempted.await(1, TimeUnit.SECONDS))

        releaseDocumentRead.countDown()
        reader.join()
        mutation.join()

        assertEquals("catalog" to "old", snapshot)
        assertEquals("new", activePath)
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
