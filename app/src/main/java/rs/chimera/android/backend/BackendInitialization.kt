package rs.chimera.android.backend

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async

/** All callers join the same recovery job; cancelling a caller does not cancel recovery. */
internal class BackendInitialization(scope: CoroutineScope, recover: suspend () -> Unit) {
    private val recovery = scope.async(start = CoroutineStart.LAZY) { recover() }

    suspend fun awaitReady() {
        recovery.await()
    }
}
