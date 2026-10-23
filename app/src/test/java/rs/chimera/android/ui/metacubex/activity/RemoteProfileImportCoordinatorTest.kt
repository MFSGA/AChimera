package rs.chimera.android.ui.metacubex.activity

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.RemoteProfileRequest

class RemoteProfileImportCoordinatorTest {
    @Test
    fun forwardsSnapshotChoiceToTheBackendOperation() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var importedAsSnapshot = false
        val coordinator = RemoteProfileImportCoordinator(scope) { _, asLocalSnapshot ->
            importedAsSnapshot = asLocalSnapshot
            "Imported profile"
        }

        try {
            assertTrue(coordinator.submit(request, asLocalSnapshot = true))
            assertTrue(importedAsSnapshot)
            assertEquals(
                RemoteProfileImportState.Completed("Imported profile"),
                coordinator.state.value,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun retainsCompletionForAnActivityRecreatedDuringImport() = runBlocking {
        val importStarted = CompletableDeferred<Unit>()
        val importResult = CompletableDeferred<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val coordinator = RemoteProfileImportCoordinator(scope) { _, _ ->
            importStarted.complete(Unit)
            importResult.await()
        }

        try {
            assertTrue(coordinator.submit(request))
            importStarted.await()
            assertEquals(RemoteProfileImportState.Importing, coordinator.state.value)
            assertFalse(coordinator.submit(request))

            importResult.complete("Imported profile")

            assertEquals(
                RemoteProfileImportState.Completed("Imported profile"),
                coordinator.state.value,
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun acknowledgedFailureAllowsOneRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0
        val coordinator = RemoteProfileImportCoordinator(scope) { _, _ ->
            attempts += 1
            if (attempts == 1) error("Temporary download failure")
            "Imported profile"
        }

        try {
            assertTrue(coordinator.submit(request))
            assertTrue(coordinator.state.value is RemoteProfileImportState.Failed)

            coordinator.acknowledgeFailure()

            assertEquals(RemoteProfileImportState.Idle, coordinator.state.value)
            assertTrue(coordinator.submit(request))
            assertEquals(
                RemoteProfileImportState.Completed("Imported profile"),
                coordinator.state.value,
            )
            assertEquals(2, attempts)
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        val request = RemoteProfileRequest(
            name = "Test profile",
            url = "https://example.com/profile.yaml",
        )
    }
}
