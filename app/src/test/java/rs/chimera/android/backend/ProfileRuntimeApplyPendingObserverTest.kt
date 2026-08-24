package rs.chimera.android.backend

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class ProfileRuntimeApplyPendingObserverTest {
    @Test
    fun existingStartupClearsCapturedPendingApplyOnRunning() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val serviceState = MutableStateFlow(ServiceState.STARTING)
        val cleared = CompletableDeferred<Pair<String, Long>>()
        val observer = ProfileRuntimeApplyPendingObserver(
            scope = scope,
            serviceState = serviceState,
            awaitReady = {},
            readPendingApply = { "profile" to 42L },
            clearPendingApply = { profileId, revision ->
                cleared.complete(profileId to revision)
            },
        )

        observer.start()
        serviceState.value = ServiceState.RUNNING

        assertEquals("profile" to 42L, withTimeout(1_000) { cleared.await() })
        scope.cancel()
    }

    @Test
    fun stoppedStartupDoesNotClearCapturedPendingApply() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val serviceState = MutableStateFlow(ServiceState.STARTING)
        val cleared = mutableListOf<Pair<String, Long>>()
        val observer = ProfileRuntimeApplyPendingObserver(
            scope = scope,
            serviceState = serviceState,
            awaitReady = {},
            readPendingApply = { "profile" to 42L },
            clearPendingApply = { profileId, revision ->
                cleared += profileId to revision
            },
        )

        observer.start()
        serviceState.value = ServiceState.STOPPED
        serviceState.value = ServiceState.RUNNING

        assertEquals(emptyList<Pair<String, Long>>(), cleared)
        scope.cancel()
    }
}
