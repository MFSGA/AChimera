package rs.chimera.android.ui.metacubex.activity

import kotlinx.coroutines.channels.ReceiveChannel
import rs.chimera.android.ui.metacubex.design.ProxyDesign

internal suspend fun consumeProxyRequests(
    requests: ReceiveChannel<ProxyDesign.Request>,
    handleRequest: suspend (ProxyDesign.Request) -> Unit,
) {
    for (request in requests) {
        handleRequest(request)
    }
}
