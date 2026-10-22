package rs.chimera.android.backend

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rs.chimera.android.backend.model.ProxyMode

/** Tracks a session-only override and restores the mode read from the active profile on reset. */
internal class SessionProxyModeCoordinator(
    private val readCurrentMode: suspend () -> ProxyMode,
    private val applyMode: suspend (ProxyMode) -> Unit,
) {
    private val mutex = Mutex()
    private var sessionDefaultMode: ProxyMode? = null
    private val mutableOverride = MutableStateFlow<ProxyMode?>(null)

    val override: StateFlow<ProxyMode?> = mutableOverride.asStateFlow()

    suspend fun setMode(mode: ProxyMode?) = mutex.withLock {
        val defaultMode = sessionDefaultMode ?: readCurrentMode().also {
            sessionDefaultMode = it
        }
        applyMode(mode ?: defaultMode)
        mutableOverride.value = mode
    }

    suspend fun resetSession() = mutex.withLock {
        sessionDefaultMode = null
        mutableOverride.value = null
    }
}
