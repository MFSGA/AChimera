package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaProxyProviderActionGateTest {
    @Test
    fun rejectsSecondActionUntilReleased() {
        val gate = MetaProxyProviderActionGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())

        gate.release()

        assertTrue(gate.tryAcquire())
    }
}
