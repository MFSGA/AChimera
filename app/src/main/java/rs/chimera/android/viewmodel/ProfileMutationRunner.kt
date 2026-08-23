package rs.chimera.android.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class ProfileMutationRunner(
    private val scope: CoroutineScope,
    private val tryBegin: () -> Boolean,
    private val end: () -> Unit,
    private val clearStatus: () -> Unit,
    private val refresh: suspend () -> Unit,
) {
    fun run(
        onChanged: () -> Unit,
        onError: (Exception) -> Unit,
        onCompleted: (Boolean) -> Unit = {},
        operation: suspend () -> Unit,
    ): Job? {
        if (!tryBegin()) {
            onCompleted(false)
            return null
        }
        clearStatus()
        return scope.launch {
            try {
                operation()
                onCompleted(true)
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: ProfileChangedDuringMutationException) {
                onChanged()
                onCompleted(false)
            } catch (error: Exception) {
                onError(error)
                onCompleted(false)
            } finally {
                end()
            }
        }
    }
}
