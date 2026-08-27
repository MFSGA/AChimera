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
        val pending = token()
        val cleared = CompletableDeferred<ProfileRuntimeApplyPendingToken>()
        val observer = ProfileRuntimeApplyPendingObserver(
            scope = scope,
            serviceState = serviceState,
            awaitReady = {},
            readPendingApply = { pending },
            clearPendingApply = cleared::complete,
        )

        observer.start()
        serviceState.value = ServiceState.RUNNING

        assertEquals(pending, withTimeout(1_000) { cleared.await() })
        scope.cancel()
    }

    @Test
    fun sourceChangeDuringStartupClearsOnlyCapturedToken() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val serviceState = MutableStateFlow(ServiceState.STARTING)
        val oldPending = token(sourceFingerprint = "old-source")
        val newPending = token(sourceFingerprint = "new-source")
        var currentPending = oldPending
        val cleared = CompletableDeferred<ProfileRuntimeApplyPendingToken>()
        val observer = ProfileRuntimeApplyPendingObserver(
            scope = scope,
            serviceState = serviceState,
            awaitReady = {},
            readPendingApply = { currentPending },
            clearPendingApply = cleared::complete,
        )

        observer.start()
        currentPending = newPending
        serviceState.value = ServiceState.RUNNING

        assertEquals(oldPending, withTimeout(1_000) { cleared.await() })
        scope.cancel()
    }

    @Test
    fun stoppedStartupDoesNotClearCapturedPendingApply() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val serviceState = MutableStateFlow(ServiceState.STARTING)
        val cleared = mutableListOf<ProfileRuntimeApplyPendingToken>()
        val observer = ProfileRuntimeApplyPendingObserver(
            scope = scope,
            serviceState = serviceState,
            awaitReady = {},
            readPendingApply = { token() },
            clearPendingApply = cleared::add,
        )

        observer.start()
        serviceState.value = ServiceState.STOPPED
        serviceState.value = ServiceState.RUNNING

        assertEquals(emptyList<ProfileRuntimeApplyPendingToken>(), cleared)
        scope.cancel()
    }

    private fun token(sourceFingerprint: String = "source") = ProfileRuntimeApplyPendingToken(
        profileId = "profile",
        profileRevision = 42L,
        sourceFingerprint = sourceFingerprint,
    )
}
