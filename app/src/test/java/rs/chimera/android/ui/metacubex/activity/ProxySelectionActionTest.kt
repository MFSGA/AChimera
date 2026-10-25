package rs.chimera.android.ui.metacubex.activity

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySelectionActionTest {
    @Test
    fun projectedSelectionRemainsVisibleBeforeBackendRequestCompletes() = runBlocking {
        val backendStarted = CompletableDeferred<Unit>()
        val finishBackend = CompletableDeferred<Unit>()
        var selected = true
        var confirmed = false
        var refreshed = false

        val request = async {
            runProxySelection(
                selectionProjected = selected,
                select = {
                    backendStarted.complete(Unit)
                    finishBackend.await()
                },
                confirmSelection = {
                    confirmed = true
                    true
                },
                refresh = { refreshed = true },
                onFailure = { throw AssertionError("unexpected selection error", it) },
            )
        }

        backendStarted.await()
        assertTrue(selected)
        assertFalse(refreshed)

        finishBackend.complete(Unit)
        request.await()
        assertTrue(selected)
        assertTrue(confirmed)
        assertFalse(refreshed)
    }

    @Test
    fun failedBackendRequestRestoresPreviousSelectionAndRefreshes() = runBlocking {
        val flow = SelectionRefreshHarness("previous", "previous")
        var reportedError: Throwable? = null
        flow.selected = "requested"

        runProxySelection(
            selectionProjected = true,
            select = { throw IllegalStateException("selection failed") },
            confirmSelection = { throw AssertionError("failed selection must not be confirmed") },
            refresh = flow::refresh,
            onFailure = { reportedError = it },
        )
        flow.finishSelection()
        flow.refreshPending()

        assertEquals("previous", flow.selected)
        assertEquals(2, flow.refreshCalls)
        assertEquals(IllegalStateException::class.java, reportedError?.javaClass)
    }

    @Test
    fun cancelledBackendRequestRestoresPreviousSelection() = runBlocking {
        val backendStarted = CompletableDeferred<Unit>()
        val flow = SelectionRefreshHarness("previous", "previous")
        var cancellationCleanupCalls = 0
        val request = launch {
            try {
                flow.selected = "requested"
                runProxySelection(
                    selectionProjected = true,
                    select = {
                        backendStarted.complete(Unit)
                        awaitCancellation()
                    },
                    confirmSelection = { throw AssertionError("cancelled selection must not be confirmed") },
                    refresh = flow::refresh,
                    onFailure = { throw AssertionError("cancellation must not be reported as failure", it) },
                    onCancellation = {
                        cancellationCleanupCalls++
                        flow.selected = "previous"
                    },
                )
            } finally {
                flow.finishSelection()
            }
        }

        backendStarted.await()
        assertEquals("requested", flow.selected)
        request.cancelAndJoin()
        assertEquals("previous", flow.selected)
        assertEquals(1, cancellationCleanupCalls)
        flow.refreshPending()

        assertEquals("previous", flow.selected)
        assertEquals(2, flow.refreshCalls)
    }

    @Test
    fun missingSelectionTargetRefreshesAfterSuccessfulBackendRequest() = runBlocking {
        var selectedOnBackend = false
        var refreshed = false
        var reportedError: Throwable? = null

        runProxySelection(
            selectionProjected = false,
            select = { selectedOnBackend = true },
            confirmSelection = { true },
            refresh = { refreshed = true },
            onFailure = { reportedError = it },
        )

        assertTrue(selectedOnBackend)
        assertTrue(refreshed)
        assertNull(reportedError)
    }

    @Test
    fun projectedSelectionRefreshesWhenConfirmedSnapshotCannotAdvance() = runBlocking {
        var refreshed = false
        var reportedError: Throwable? = null

        runProxySelection(
            selectionProjected = true,
            select = {},
            confirmSelection = { false },
            refresh = { refreshed = true },
            onFailure = { reportedError = it },
        )

        assertTrue(refreshed)
        assertNull(reportedError)
    }

    private class SelectionRefreshHarness(
        initialSelection: String,
        private val backendSelection: String,
    ) {
        private val refreshGate = ProxyGroupRefreshGate()
        private var selecting = true
        var selected = initialSelection
        var refreshCalls = 0
            private set

        init {
            refreshGate.selectionStarted()
        }

        suspend fun refresh() {
            refreshCalls++
            delay(1)
            val revision = refreshGate.requestRefresh(selecting, refreshing = false) ?: return
            if (refreshGate.shouldApply(revision, selecting)) selected = backendSelection
        }

        fun finishSelection() {
            selecting = false
            refreshGate.selectionFinished()
        }

        suspend fun refreshPending() {
            if (refreshGate.takePendingRefresh(selecting, refreshing = false)) refresh()
        }
    }
}
