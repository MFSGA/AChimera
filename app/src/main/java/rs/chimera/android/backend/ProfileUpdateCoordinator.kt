package rs.chimera.android.backend

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ProfileUpdateCoordinator {
    private val locks = mutableMapOf<String, LockEntry>()

    internal val retainedLockCount: Int
        get() = synchronized(locks) { locks.size }

    suspend fun <T> withLock(
        profileId: String,
        block: suspend () -> T,
    ): T {
        require(profileId.isNotBlank()) { "Profile id is empty" }
        val entry = synchronized(locks) {
            locks.getOrPut(profileId, ::LockEntry).also { it.users += 1 }
        }
        return try {
            entry.mutex.withLock { block() }
        } finally {
            synchronized(locks) {
                check(entry.users > 0) { "Profile update lock usage underflow" }
                entry.users -= 1
                if (entry.users == 0 && locks[profileId] === entry) {
                    locks.remove(profileId)
                }
            }
        }
    }

    private class LockEntry(
        val mutex: Mutex = Mutex(),
        var users: Int = 0,
    )
}
