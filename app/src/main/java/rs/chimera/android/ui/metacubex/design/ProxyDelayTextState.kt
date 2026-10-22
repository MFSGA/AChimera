package rs.chimera.android.ui.metacubex.design

/** Mirrors CMFA's ProxyViewState delay cache for each bound proxy card. */
internal class ProxyDelayTextState {
    private var groupName: String? = null
    private var proxyName: String? = null
    private var delay: Int = 0

    var text: String = ""
        private set

    fun update(entry: ProxyDisplayEntry?): String {
        val nextGroupName = entry?.selectionGroupName
        val nextProxyName = entry?.name
        if (groupName != nextGroupName || proxyName != nextProxyName) {
            groupName = nextGroupName
            proxyName = nextProxyName
            delay = 0
            text = ""
        }

        val nextDelay = entry?.delayValue ?: 0
        if (delay != nextDelay) {
            delay = nextDelay
            text = if (nextDelay in 0..Short.MAX_VALUE) nextDelay.toString() else ""
        }
        return text
    }
}
