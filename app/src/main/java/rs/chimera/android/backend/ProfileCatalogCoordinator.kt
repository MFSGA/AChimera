package rs.chimera.android.backend

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class ProfileCatalogCoordinator {
    private val lock = ReentrantLock()

    fun <T> withLock(block: () -> T): T = lock.withLock(block)
}
