package rs.chimera.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ProfileFileSelectionController<T>(
    private val scope: CoroutineScope,
) {
    private val generations = LatestOperationGate()

    var selection by mutableStateOf<T?>(null)
        private set

    fun select(
        load: suspend () -> T,
        onSelected: () -> Unit = {},
        onError: (Exception) -> Unit = {},
    ): Job {
        val generation = generations.next()
        return scope.launch {
            try {
                val value = load()
                if (generations.isCurrent(generation)) {
                    selection = value
                    onSelected()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generations.isCurrent(generation)) {
                    selection = null
                    onError(error)
                }
            }
        }
    }

    fun clear() {
        generations.next()
        selection = null
    }
}
