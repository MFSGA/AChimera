package rs.chimera.android.service

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal suspend fun <T> withVpnCleanupContext(block: suspend () -> T): T =
    withContext(NonCancellable) { block() }
