package rs.chimera.android.ui.metacubex.activity

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.ui.metacubex.design.ProxyDesign

class ProxyRequestLoopTest {
    @Test
    fun nodeSelectionWaitsForSlowModeSwitchAndThenRunsInArrivalOrder() = runBlocking {
        val requests = Channel<ProxyDesign.Request>(Channel.UNLIMITED)
        val modeSwitchStarted = CompletableDeferred<Unit>()
        val finishModeSwitch = CompletableDeferred<Unit>()
        val handledEvents = mutableListOf<String>()
        val consumer = launch {
            consumeProxyRequests(requests) { request ->
                when (request) {
                    is ProxyDesign.Request.SwitchMode -> {
                        handledEvents += "mode-start:${request.mode}"
                        modeSwitchStarted.complete(Unit)
                        finishModeSwitch.await()
                        handledEvents += "mode-finish:${request.mode}"
                    }
                    is ProxyDesign.Request.SelectProxy -> {
                        handledEvents += "select:${request.groupName}:${request.proxyName}"
                    }
                    else -> handledEvents += request::class.simpleName.orEmpty()
                }
            }
        }
        val modeRequest = ProxyDesign.Request.SwitchMode(ProxyMode.GLOBAL)
        val selectionRequest = ProxyDesign.Request.SelectProxy("Proxy", "node-a")

        requests.send(modeRequest)
        modeSwitchStarted.await()
        requests.send(selectionRequest)
        yield()

        assertEquals(listOf("mode-start:GLOBAL"), handledEvents)

        finishModeSwitch.complete(Unit)
        requests.close()
        consumer.join()

        assertEquals(
            listOf("mode-start:GLOBAL", "mode-finish:GLOBAL", "select:Proxy:node-a"),
            handledEvents,
        )
    }

    @Test
    fun rapidNodeSelectionsKeepBackendRequestsSerializedInArrivalOrder() = runBlocking {
        val requests = Channel<ProxyDesign.Request>(Channel.UNLIMITED)
        val firstSelectionStarted = CompletableDeferred<Unit>()
        val finishFirstSelection = CompletableDeferred<Unit>()
        val secondSelectionStarted = CompletableDeferred<Unit>()
        val handledEvents = mutableListOf<String>()
        val consumer = launch {
            consumeProxyRequests(requests) { request ->
                val selection = request as ProxyDesign.Request.SelectProxy
                handledEvents += "start:${selection.proxyName}"
                if (selection.proxyName == "node-b") {
                    firstSelectionStarted.complete(Unit)
                    finishFirstSelection.await()
                } else {
                    secondSelectionStarted.complete(Unit)
                }
                handledEvents += "finish:${selection.proxyName}"
            }
        }

        requests.send(ProxyDesign.Request.SelectProxy("Proxy", "node-b", selectionToken = 1L))
        firstSelectionStarted.await()
        requests.send(ProxyDesign.Request.SelectProxy("Proxy", "node-a", selectionToken = 2L))
        yield()

        assertEquals(listOf("start:node-b"), handledEvents)

        finishFirstSelection.complete(Unit)
        secondSelectionStarted.await()
        requests.close()
        consumer.join()

        assertEquals(
            listOf("start:node-b", "finish:node-b", "start:node-a", "finish:node-a"),
            handledEvents,
        )
    }
}
