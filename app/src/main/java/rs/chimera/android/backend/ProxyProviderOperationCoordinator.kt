package rs.chimera.android.backend

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ProxyProviderOperationCoordinator {
    private val locks = mutableMapOf<String, LockEntry>()

    suspend fun <T> withLock(
        providerName: String,
        block: suspend () -> T,
    ): T {
        require(providerName.isNotBlank()) { "Proxy provider name is empty" }
        val entry = synchronized(locks) {
            locks.getOrPut(providerName, ::LockEntry).also { it.users += 1 }
        }
        return try {
            entry.mutex.withLock { block() }
        } finally {
            synchronized(locks) {
                check(entry.users > 0) { "Proxy provider lock usage underflow" }
                entry.users -= 1
                if (entry.users == 0 && locks[providerName] === entry) {
                    locks.remove(providerName)
                }
            }
        }
    }

    private class LockEntry(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )
}
