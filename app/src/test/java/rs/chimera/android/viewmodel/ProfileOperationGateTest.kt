package rs.chimera.android.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileOperationGateTest {
    @Test
    fun `only one operation can hold the gate`() {
        val gate = ProfileOperationGate()

        assertTrue(gate.tryAcquire())
        assertTrue(gate.isActive)
        assertFalse(gate.tryAcquire())

        gate.release()
        assertFalse(gate.isActive)
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `release without owner fails fast`() {
        val gate = ProfileOperationGate()

        assertThrows(IllegalStateException::class.java) {
            gate.release()
        }
    }
}
