package rs.chimera.android.service

internal interface VpnRuntimeControl {
    fun protectSocket(fd: Int): Boolean

    fun onCoreStopped(message: String)

    suspend fun stopVpn()

    suspend fun restartVpn()
}

internal object VpnRuntimeRegistry {
    private val lock = Any()

    @Volatile
    private var control: VpnRuntimeControl? = null

    val isRegistered: Boolean
        get() = control != null

    fun register(runtime: VpnRuntimeControl) {
        synchronized(lock) {
            control = runtime
        }
    }

    fun unregister(runtime: VpnRuntimeControl) {
        synchronized(lock) {
            if (control === runtime) {
                control = null
            }
        }
    }

    fun protectSocket(fd: Int): Boolean = control?.protectSocket(fd) == true

    fun dispatchCoreStopped(message: String): Boolean {
        val runtime = control ?: return false
        runtime.onCoreStopped(message)
        return true
    }

    suspend fun stopVpn(): Boolean {
        val runtime = control ?: return false
        runtime.stopVpn()
        return true
    }

    suspend fun restartVpn() {
        val runtime = checkNotNull(control) { "VPN service is unavailable" }
        runtime.restartVpn()
    }
}
