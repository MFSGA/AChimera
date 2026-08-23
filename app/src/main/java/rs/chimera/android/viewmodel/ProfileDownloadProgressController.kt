package rs.chimera.android.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import uniffi.chimera_ffi.DownloadProgress

internal class ProfileDownloadProgressController(
    scope: CoroutineScope,
    private val shouldPublish: () -> Boolean,
    private val publish: (DownloadProgress?) -> Unit,
) {
    private val operations = LatestOperationGate()
    private val updates = Channel<Pair<Long, DownloadProgress>>(Channel.CONFLATED)

    init {
        scope.launch {
            for ((generation, progress) in updates) {
                if (shouldPublish() && operations.isCurrent(generation)) {
                    publish(progress)
                }
            }
        }
    }

    fun begin(): Long {
        publish(null)
        return operations.next()
    }

    fun report(generation: Long, progress: DownloadProgress) {
        updates.trySend(generation to progress)
    }

    fun end() {
        operations.next()
        publish(null)
    }
}
