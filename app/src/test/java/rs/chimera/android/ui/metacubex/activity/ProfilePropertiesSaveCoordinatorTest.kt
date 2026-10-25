package rs.chimera.android.ui.metacubex.activity

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfilePropertiesSaveCoordinatorTest {
    @Test
    fun duplicateSaveIsRejectedAndCompletionRemainsAvailableAfterRecreation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val release = CompletableDeferred<Unit>()
        val coordinator = ProfilePropertiesSaveCoordinator(scope)
        var writes = 0
        try {
            assertTrue(coordinator.submit {
                writes++
                release.await()
            })
            assertEquals(ProfilePropertiesSaveState.Saving, coordinator.state.value)
            assertFalse(coordinator.submit { error("Duplicate write") })
            release.complete(Unit)
            assertEquals(1, writes)
            assertEquals(ProfilePropertiesSaveState.Saved, coordinator.state.value)
            assertFalse(coordinator.submit { error("Completion must remain terminal") })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun failureRequiresAcknowledgementBeforeRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = ProfilePropertiesSaveCoordinator(scope)
        val failure = IllegalStateException("Synthetic write failure")
        try {
            assertTrue(coordinator.submit { throw failure })
            assertSame(failure, (coordinator.state.value as ProfilePropertiesSaveState.Failed).error)
            assertFalse(coordinator.submit { error("Unacknowledged retry") })
            coordinator.acknowledgeFailure()
            assertTrue(coordinator.submit { })
            assertEquals(ProfilePropertiesSaveState.Saved, coordinator.state.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun cancellationDoesNotBecomeAUserFacingSaveFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = ProfilePropertiesSaveCoordinator(scope)
        assertTrue(coordinator.submit { awaitCancellation() })
        scope.cancel()
        assertEquals(ProfilePropertiesSaveState.Idle, coordinator.state.value)
    }
}
