package rs.chimera.android.ui.metacubex.activity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.ui.metacubex.design.ProxyDesign

class ProxyModeSwitchActionTest {
    @Test
    fun queuedModeSwitchIsNotBlockedBySelectionsQueuedAfterIt() = runBlocking {
        val requests = Channel<ProxyDesign.Request>(Channel.UNLIMITED)
        val firstSelectionStarted = CompletableDeferred<Unit>()
        val finishFirstSelection = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        var pendingSelections = 0
        val consumer = launch {
            consumeProxyRequests(requests) { request ->
                when (request) {
                    is ProxyDesign.Request.SelectProxy -> {
                        events += "select-start:${request.proxyName}"
                        if (request.proxyName == "node-a") {
                            firstSelectionStarted.complete(Unit)
                            finishFirstSelection.await()
                        }
                        pendingSelections--
                        events += "select-finish:${request.proxyName}"
                    }
                    is ProxyDesign.Request.SwitchMode -> {
                        assertEquals(1, pendingSelections)
                        assertTrue(canStartProxyModeSwitch(switchingMode = false))
                        events += "mode:${request.mode}"
                    }
                    else -> Unit
                }
            }
        }

        pendingSelections++
        requests.send(ProxyDesign.Request.SelectProxy("Proxy", "node-a"))
        firstSelectionStarted.await()
        requests.send(ProxyDesign.Request.SwitchMode(ProxyMode.GLOBAL))
        pendingSelections++
        requests.send(ProxyDesign.Request.SelectProxy("Proxy", "node-b"))
        finishFirstSelection.complete(Unit)
        requests.close()
        consumer.join()

        assertEquals(
            listOf(
                "select-start:node-a",
                "select-finish:node-a",
                "mode:GLOBAL",
                "select-start:node-b",
                "select-finish:node-b",
            ),
            events,
        )
    }

    @Test
    fun failedSwitchRestoresBackendConfirmedMode() = runBlocking {
        val displayedModes = mutableListOf<ProxyMode?>()
        val failure = IllegalStateException("mode rejected")
        var reportedFailure: Throwable? = null
        var refreshCount = 0

        runProxyModeSwitch(
            mode = ProxyMode.RULE,
            applyMode = { throw failure },
            currentMode = { ProxyMode.DIRECT },
            setDisplayedMode = displayedModes::add,
            refresh = { refreshCount++ },
            onFailure = { reportedFailure = it },
        )

        assertEquals(listOf(ProxyMode.DIRECT), displayedModes)
        assertSame(failure, reportedFailure)
        assertEquals(0, refreshCount)
    }

    @Test
    fun cancelledSwitchRestoresBackendConfirmedModeAndRethrowsCancellation() = runBlocking {
        val displayedModes = mutableListOf<ProxyMode?>()
        val cancellation = CancellationException("mode switch cancelled")
        var reportedFailure = false
        var refreshCount = 0
        var thrown: Throwable? = null

        try {
            runProxyModeSwitch(
                mode = ProxyMode.RULE,
                applyMode = { throw cancellation },
                currentMode = { ProxyMode.DIRECT },
                setDisplayedMode = displayedModes::add,
                refresh = { refreshCount++ },
                onFailure = { reportedFailure = true },
            )
        } catch (error: Throwable) {
            thrown = error
        }

        assertSame(cancellation, thrown)
        assertEquals(listOf(ProxyMode.DIRECT), displayedModes)
        assertFalse(reportedFailure)
        assertEquals(0, refreshCount)
    }

    @Test
    fun successfulSwitchKeepsRequestedModeAndRefreshesGroups() = runBlocking {
        val displayedModes = mutableListOf<ProxyMode?>()
        var appliedMode: ProxyMode? = null
        var refreshed = false
        var reportedFailure = false

        runProxyModeSwitch(
            mode = ProxyMode.RULE,
            applyMode = { appliedMode = it },
            currentMode = { ProxyMode.DIRECT },
            setDisplayedMode = displayedModes::add,
            refresh = { refreshed = true },
            onFailure = { reportedFailure = true },
        )

        assertEquals(ProxyMode.RULE, appliedMode)
        assertEquals(listOf(ProxyMode.RULE), displayedModes)
        assertTrue(refreshed)
        assertFalse(reportedFailure)
    }
}
