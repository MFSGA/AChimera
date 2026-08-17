package rs.chimera.android.viewmodel

import java.util.concurrent.atomic.AtomicBoolean

internal class ProfileOperationGate {
    private val active = AtomicBoolean(false)

    val isActive: Boolean
        get() = active.get()

    fun tryAcquire(): Boolean = active.compareAndSet(false, true)

    fun release() {
        check(active.compareAndSet(true, false)) { "Profile operation gate is not active" }
    }
}
