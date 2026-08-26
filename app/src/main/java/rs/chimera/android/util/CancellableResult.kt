package rs.chimera.android.util

import kotlinx.coroutines.CancellationException

internal inline fun <T> runCatchingRecoverable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: Exception) {
        Result.failure(error)
    }

internal suspend fun <T> runCatchingPreservingCancellation(
    block: suspend () -> T,
): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
