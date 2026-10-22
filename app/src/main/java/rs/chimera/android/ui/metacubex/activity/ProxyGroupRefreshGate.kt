package rs.chimera.android.ui.metacubex.activity

internal class ProxyGroupRefreshGate {
    private var revision = 0L
    private var refreshPending = false

    fun selectionStarted() {
        revision++
    }

    fun selectionFinished() {
        revision++
    }

    fun requestRefresh(selecting: Boolean, refreshing: Boolean): Long? {
        if (selecting || refreshing) {
            deferRefresh()
            return null
        }

        refreshPending = false
        return revision
    }

    fun deferRefresh() {
        revision++
        refreshPending = true
    }

    fun shouldApply(
        refreshRevision: Long,
        selecting: Boolean,
        serviceRunning: Boolean = true,
    ): Boolean {
        if (!serviceRunning) return false
        if (selecting || refreshRevision != revision) {
            refreshPending = true
            return false
        }

        return true
    }

    fun takePendingRefresh(selecting: Boolean, refreshing: Boolean): Boolean {
        if (selecting || refreshing || !refreshPending) return false

        refreshPending = false
        return true
    }
}
