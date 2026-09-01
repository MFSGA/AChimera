package rs.chimera.android.ui.metacubex.activity

internal class MetaProxyProviderActionGate {
    private var active = false

    @Synchronized
    fun tryAcquire(): Boolean {
        if (active) return false
        active = true
        return true
    }

    @Synchronized
    fun release() {
        active = false
    }
}
