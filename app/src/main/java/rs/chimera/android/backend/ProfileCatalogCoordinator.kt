package rs.chimera.android.backend

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class ProfileCatalogCoordinator {
    private val lock = ReentrantLock()

    fun <T> withLock(block: () -> T): T = lock.withLock(block)
}

internal fun <T> readConsistentProfileCatalogSnapshot(
    coordinator: ProfileCatalogCoordinator,
    readDocument: () -> T?,
    readActivePath: () -> String?,
): Pair<T, String?>? = coordinator.withLock {
    val document = readDocument() ?: return@withLock null
    document to readActivePath()
}
