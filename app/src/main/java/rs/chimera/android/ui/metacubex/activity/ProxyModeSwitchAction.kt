package rs.chimera.android.ui.metacubex.activity

import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.util.runCatchingPreservingCancellation

internal fun canStartProxyModeSwitch(switchingMode: Boolean): Boolean = !switchingMode

internal suspend fun runProxyModeSwitch(
    mode: ProxyMode?,
    applyMode: suspend (ProxyMode?) -> Unit,
    currentMode: () -> ProxyMode?,
    setDisplayedMode: (ProxyMode?) -> Unit,
    refresh: suspend () -> Unit,
    onFailure: suspend (Throwable) -> Unit,
) {
    var modeChangeSucceeded = false
    try {
        val result = runCatchingPreservingCancellation { applyMode(mode) }
        if (result.isSuccess) {
            modeChangeSucceeded = true
            setDisplayedMode(mode)
            refresh()
        } else {
            val error = result.exceptionOrNull()
            if (error != null) onFailure(error)
        }
    } finally {
        if (!modeChangeSucceeded) setDisplayedMode(currentMode())
    }
}
