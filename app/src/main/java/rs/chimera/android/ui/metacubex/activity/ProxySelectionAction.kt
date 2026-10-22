package rs.chimera.android.ui.metacubex.activity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import rs.chimera.android.util.runCatchingPreservingCancellation

internal suspend fun runProxySelection(
    selectionProjected: Boolean,
    select: suspend () -> Unit,
    confirmSelection: () -> Boolean,
    refresh: suspend () -> Unit,
    onFailure: suspend (Throwable) -> Unit,
) {
    val result = try {
        runCatchingPreservingCancellation(select)
    } catch (cancellation: CancellationException) {
        withContext(NonCancellable) { refresh() }
        throw cancellation
    }
    if (result.isSuccess) {
        val confirmed = confirmSelection()
        if (!selectionProjected || !confirmed) refresh()
    } else {
        onFailure(result.exceptionOrNull() ?: return)
        refresh()
    }
}
