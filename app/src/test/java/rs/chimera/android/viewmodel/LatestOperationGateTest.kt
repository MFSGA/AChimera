package rs.chimera.android.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestOperationGateTest {
    @Test
    fun newerOperationInvalidatesOlderToken() {
        val gate = LatestOperationGate()
        val first = gate.next()
        val second = gate.next()

        assertFalse(gate.isCurrent(first))
        assertTrue(gate.isCurrent(second))
    }

    @Test
    fun issuedTokenRemainsCurrentUntilNextOperation() {
        val gate = LatestOperationGate()
        val token = gate.next()

        assertTrue(gate.isCurrent(token))
        assertTrue(gate.isCurrent(token))
    }
}
