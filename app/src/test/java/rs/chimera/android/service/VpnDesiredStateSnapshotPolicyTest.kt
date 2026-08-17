package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnDesiredStateSnapshotPolicyTest {
    @Test
    fun parsesOneConsistentPreferenceSnapshot() {
        val snapshot = VpnDesiredStateSnapshotPolicy.parse(
            mapOf(
                VpnDesiredStateStore.KEY_SHOULD_RUN to true,
                VpnDesiredStateStore.KEY_UPDATED_AT to 42L,
                VpnDesiredStateStore.KEY_REASON to VpnDesiredStateReason.USER_START.name,
            ),
        )

        assertTrue(snapshot.shouldRun)
        assertEquals(42L, snapshot.updatedAt)
        assertEquals(VpnDesiredStateReason.USER_START, snapshot.reason)
    }

    @Test
    fun corruptedValuesFallBackToStoppedState() {
        val snapshot = VpnDesiredStateSnapshotPolicy.parse(
            mapOf(
                VpnDesiredStateStore.KEY_SHOULD_RUN to "true",
                VpnDesiredStateStore.KEY_UPDATED_AT to "42",
                VpnDesiredStateStore.KEY_REASON to "UNKNOWN",
            ),
        )

        assertFalse(snapshot.shouldRun)
        assertEquals(0L, snapshot.updatedAt)
        assertEquals(VpnDesiredStateReason.USER_STOP, snapshot.reason)
    }
}
