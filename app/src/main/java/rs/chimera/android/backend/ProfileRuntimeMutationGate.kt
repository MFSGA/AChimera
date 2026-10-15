package rs.chimera.android.backend

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ProfileRuntimeMutationGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}
