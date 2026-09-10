package rs.chimera.android.backend

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class StateFlowViewTest {
    @Test
    fun projectionIsImmediateAndSuppressesUnchangedValues() = runBlocking {
        val source = MutableStateFlow(1 to "old")
        val view = StateFlowView(source) { it.first }
        val observed = mutableListOf<Int>()
        val collection = launch(start = CoroutineStart.UNDISPATCHED) { view.take(2).toList(observed) }
        source.value = 1 to "new"
        yield()
        assertEquals(listOf(1), observed)
        source.value = 2 to "new"
        assertEquals(2, view.value)
        assertEquals(listOf(2), view.replayCache)
        collection.join()
        assertEquals(listOf(1, 2), observed)
    }
}
