package rs.chimera.android.backend

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileUpdateCoordinatorTest {
    @Test
    fun serializesMutationsForSameProfile() = runBlocking {
        val coordinator = ProfileUpdateCoordinator()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withLock("profile") {
                releaseFirst.await()
            }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withLock("profile") {
                secondEntered.complete(Unit)
            }
        }

        assertFalse(secondEntered.isCompleted)
        releaseFirst.complete(Unit)
        first.await()
        second.await()

        assertTrue(secondEntered.isCompleted)
        assertEquals(0, coordinator.retainedLockCount)
    }

    @Test
    fun allowsDifferentProfilesToMutateConcurrently() = runBlocking {
        val coordinator = ProfileUpdateCoordinator()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withLock("profile-a") {
                releaseFirst.await()
            }
        }
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withLock("profile-b") {
                secondEntered.complete(Unit)
            }
        }

        second.await()
        assertTrue(secondEntered.isCompleted)
        assertEquals(1, coordinator.retainedLockCount)

        releaseFirst.complete(Unit)
        first.await()
        assertEquals(0, coordinator.retainedLockCount)
    }

    @Test
    fun cancelledWaiterReleasesLockReference() = runBlocking {
        val coordinator = ProfileUpdateCoordinator()
        val releaseFirst = CompletableDeferred<Unit>()

        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withLock("profile") {
                releaseFirst.await()
            }
        }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.withLock("profile") { error("cancelled waiter entered") }
        }

        waiter.cancelAndJoin()
        assertEquals(1, coordinator.retainedLockCount)

        releaseFirst.complete(Unit)
        first.await()
        assertEquals(0, coordinator.retainedLockCount)
    }
}
