package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Test

class VpnRecoveryPolicyTest {
    @Test
    fun stoppedDesiredStateIsNeverRecovered() {
        assertEquals(
            VpnRecoveryDecision.DISCARD_STOPPED,
            VpnRecoveryPolicy.decide(
                snapshot = stoppedSnapshot(),
                profileAvailable = true,
                permissionGranted = true,
            ),
        )
    }

    @Test
    fun missingProfileBlocksRecovery() {
        assertEquals(
            VpnRecoveryDecision.BLOCK_MISSING_PROFILE,
            VpnRecoveryPolicy.decide(
                snapshot = runningSnapshot(),
                profileAvailable = false,
                permissionGranted = true,
            ),
        )
    }

    @Test
    fun missingPermissionBlocksRecovery() {
        assertEquals(
            VpnRecoveryDecision.BLOCK_PERMISSION_REQUIRED,
            VpnRecoveryPolicy.decide(
                snapshot = runningSnapshot(),
                profileAvailable = true,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun completeRunningIntentCanRecover() {
        assertEquals(
            VpnRecoveryDecision.RESTORE,
            VpnRecoveryPolicy.decide(
                snapshot = runningSnapshot(),
                profileAvailable = true,
                permissionGranted = true,
            ),
        )
    }

    private fun runningSnapshot() = VpnDesiredStateSnapshot(
        shouldRun = true,
        updatedAt = 1_000,
        reason = VpnDesiredStateReason.USER_START,
    )

    private fun stoppedSnapshot() = VpnDesiredStateSnapshot(
        shouldRun = false,
        updatedAt = 1_000,
        reason = VpnDesiredStateReason.USER_STOP,
    )
}
