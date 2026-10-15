package rs.chimera.android.backend

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileRuntimeMutationGateTest {
    @Test
    fun serializesProfileDeletionWithRuntimeMutations() = runBlocking {
        val gate = ProfileRuntimeMutationGate()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val activation = async(start = CoroutineStart.UNDISPATCHED) {
            gate.withLock {
                events += "activation-started"
                entered.complete(Unit)
                release.await()
                events += "activation-finished"
            }
        }
        entered.await()

        val deletion = async(start = CoroutineStart.UNDISPATCHED) {
            gate.withLock { events += "deletion-started" }
        }

        assertEquals(listOf("activation-started"), events)
        release.complete(Unit)
        activation.await()
        deletion.await()

        assertEquals(
            listOf("activation-started", "activation-finished", "deletion-started"),
            events,
        )
    }
}
