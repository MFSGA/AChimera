package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class RuntimeOperationPolicyTest {
    @Test
    fun runningServicePassesWithoutEvaluatingMessage() {
        var messageEvaluated = false

        RuntimeOperationPolicy.requireRunning(ServiceState.RUNNING) {
            messageEvaluated = true
            "not running"
        }

        assertEquals(false, messageEvaluated)
    }

    @Test
    fun stoppedServiceUsesFullNotRunningMessage() {
        val error = runCatching {
            RuntimeOperationPolicy.requireRunning(ServiceState.STOPPED) {
                "VPN must be running before using runtime controls."
            }
        }.exceptionOrNull()

        assertEquals(
            "VPN must be running before using runtime controls.",
            error?.message,
        )
    }
}
