package rs.chimera.android.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import rs.chimera.android.backend.model.ServiceState

internal class ProfileRuntimeApplyPendingObserver(
    private val scope: CoroutineScope,
    private val serviceState: StateFlow<ServiceState>,
    private val awaitReady: suspend () -> Unit,
    private val readPendingApply: suspend () -> Pair<String, Long>?,
    private val clearPendingApply: suspend (profileId: String, revision: Long) -> Unit,
) {
    fun start() {
        scope.launch {
            val pendingTracker = ProfileRuntimeApplyPendingTracker()
            serviceState.collect { state ->
                awaitReady()
                val pendingApply = if (state == ServiceState.STARTING) {
                    readPendingApply()
                } else {
                    null
                }
                pendingTracker.onServiceState(state, pendingApply)?.let { (profileId, revision) ->
                    clearPendingApply(profileId, revision)
                }
            }
        }
    }
}
