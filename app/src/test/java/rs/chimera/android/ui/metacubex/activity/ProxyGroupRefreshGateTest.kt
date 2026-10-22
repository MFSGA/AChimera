package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyGroupRefreshGateTest {
    @Test
    fun staleSnapshotCannotReplaceOptimisticSelectionAfterBackendCompletes() {
        val gate = ProxyGroupRefreshGate()
        val oldRefreshRevision = gate.requestRefresh(selecting = false, refreshing = false)!!
        var selectedProxy = "old-proxy"

        gate.selectionStarted()
        selectedProxy = "new-proxy"
        gate.selectionFinished()

        if (gate.shouldApply(oldRefreshRevision, selecting = false)) {
            selectedProxy = "old-proxy"
        }

        assertEquals("new-proxy", selectedProxy)
        assertTrue(gate.takePendingRefresh(selecting = false, refreshing = false))
        assertFalse(gate.takePendingRefresh(selecting = false, refreshing = false))
    }

    @Test
    fun refreshRequestedDuringSelectionRunsAfterSelectionFinishes() {
        val gate = ProxyGroupRefreshGate()

        gate.selectionStarted()
        assertNull(gate.requestRefresh(selecting = true, refreshing = false))
        assertFalse(gate.takePendingRefresh(selecting = true, refreshing = false))

        gate.selectionFinished()
        assertTrue(gate.takePendingRefresh(selecting = false, refreshing = false))
    }

    @Test
    fun staleRefreshKeepsFollowUpQueuedUntilSelectionAndRefreshFinish() {
        val gate = ProxyGroupRefreshGate()
        val refreshRevision = gate.requestRefresh(selecting = false, refreshing = false)!!

        gate.selectionStarted()
        assertFalse(gate.shouldApply(refreshRevision, selecting = true))
        assertFalse(gate.takePendingRefresh(selecting = true, refreshing = false))
        gate.selectionFinished()
        assertFalse(gate.takePendingRefresh(selecting = false, refreshing = true))
        assertTrue(gate.takePendingRefresh(selecting = false, refreshing = false))
    }

    @Test
    fun modeSwitchDuringRefreshInvalidatesOldSnapshotAndQueuesFollowUp() {
        val gate = ProxyGroupRefreshGate()
        val refreshRevision = gate.requestRefresh(selecting = false, refreshing = false)!!

        gate.selectionStarted()
        assertNull(gate.requestRefresh(selecting = true, refreshing = true))
        assertFalse(gate.shouldApply(refreshRevision, selecting = true))
        assertFalse(gate.takePendingRefresh(selecting = true, refreshing = true))

        gate.selectionFinished()
        assertFalse(gate.takePendingRefresh(selecting = false, refreshing = true))
        assertTrue(gate.takePendingRefresh(selecting = false, refreshing = false))
    }

    @Test
    fun freshSnapshotCanApplyWhenSelectionHasNotChanged() {
        val gate = ProxyGroupRefreshGate()
        val refreshRevision = gate.requestRefresh(selecting = false, refreshing = false)!!

        assertTrue(gate.shouldApply(refreshRevision, selecting = false))
    }

    @Test
    fun refreshResultCannotApplyAfterServiceStops() {
        val gate = ProxyGroupRefreshGate()
        val refreshRevision = gate.requestRefresh(selecting = false, refreshing = false)!!

        assertFalse(
            gate.shouldApply(
                refreshRevision,
                selecting = false,
                serviceRunning = false,
            ),
        )
        assertFalse(gate.takePendingRefresh(selecting = false, refreshing = false))
    }

    @Test
    fun refreshRequestedWhileAnotherRefreshRunsInvalidatesOldSnapshot() {
        val gate = ProxyGroupRefreshGate()
        val oldRefreshRevision = gate.requestRefresh(selecting = false, refreshing = false)!!

        assertNull(gate.requestRefresh(selecting = false, refreshing = true))

        assertFalse(gate.shouldApply(oldRefreshRevision, selecting = false))
        assertFalse(gate.takePendingRefresh(selecting = false, refreshing = true))
        assertTrue(gate.takePendingRefresh(selecting = false, refreshing = false))
    }
}
