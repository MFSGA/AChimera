package rs.chimera.android.backend

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Read-only projection with an immediate value, without a second mutable state or scope. */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class StateFlowView<S, T>(
    private val source: StateFlow<S>,
    private val transform: (S) -> T,
) : StateFlow<T> {
    override val value: T get() = transform(source.value)
    override val replayCache: List<T> get() = listOf(value)

    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        source.map(transform).distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}
