package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySelectionAnimationTrackerTest {
    @Test
    fun consecutiveSelectionsKeepEarlierNodesWithinTheTransitionWindow() {
        val tracker = ProxySelectionAnimationTracker()

        assertEquals(
            mapOf("selector" to mapOf("node-a" to 420L, "node-b" to 420L)),
            tracker.record("selector", "node-a", "node-b", frameTime = 100L),
        )
        assertEquals(
            mapOf("selector" to mapOf("node-a" to 420L, "node-b" to 420L)),
            tracker.snapshot(frameTime = 164L),
        )
        assertEquals(
            mapOf("selector" to mapOf("node-a" to 420L, "node-b" to 484L, "node-c" to 484L)),
            tracker.record("selector", "node-b", "node-c", frameTime = 164L),
        )

        tracker.clearGroup("selector")

        assertEquals(emptyMap<String, Map<String, Long>>(), tracker.snapshot(frameTime = 165L))

        tracker.clear()

        assertEquals(
            mapOf("selector" to mapOf("node-c" to 520L, "node-d" to 520L)),
            tracker.record("selector", "node-c", "node-d", frameTime = 200L),
        )
    }

    @Test
    fun completedTransitionMarkersExpire() {
        val tracker = ProxySelectionAnimationTracker()
        tracker.record("selector", "node-a", "node-b", frameTime = 100L)

        assertEquals(mapOf("selector" to mapOf("node-a" to 420L, "node-b" to 420L)), tracker.snapshot(419L))
        assertEquals(emptyMap<String, Map<String, Long>>(), tracker.snapshot(420L))
    }

    @Test
    fun selectionsInDifferentGroupsKeepSeparateTransitionNodes() {
        val tracker = ProxySelectionAnimationTracker()

        tracker.record("selector-a", "node-a", "node-b", frameTime = 100L)

        assertEquals(
            mapOf(
                "selector-a" to mapOf("node-a" to 420L, "node-b" to 420L),
                "selector-b" to mapOf("node-c" to 420L, "node-d" to 420L),
            ),
            tracker.record("selector-b", "node-c", "node-d", frameTime = 100L),
        )
    }

    @Test
    fun removingSelectorGroupDropsItsCachedTransitionMarkers() {
        val tracker = ProxySelectionAnimationTracker()
        tracker.record("selector-a", "node-a", "node-b", frameTime = 100L)
        tracker.record("selector-b", "node-c", "node-d", frameTime = 100L)

        tracker.retainGroups(setOf("selector-b"))

        assertEquals(
            mapOf("selector-b" to mapOf("node-c" to 420L, "node-d" to 420L)),
            tracker.snapshot(frameTime = 101L),
        )
    }

    @Test
    fun rapidSelectionRetargetsTheMiddleNodeAndKeepsEarlierAnimationRunning() {
        val tracker = ProxySelectionAnimationTracker()
        val before = mapOf(
            "node-a" to entry("node-a", selected = true, markers = emptyMap()),
            "node-b" to entry("node-b", selected = false, markers = emptyMap()),
            "node-c" to entry("node-c", selected = false, markers = emptyMap()),
        )
        val afterB = entriesAfterSelection(
            selectedNode = "node-b",
            markers = tracker.record("selector", "node-a", "node-b", frameTime = 100L),
        )

        val nodeATransition = ProxySelectionColorTransition().apply {
            snap(SELECTED, frameTime = 100L)
        }
        assertTrue(nodeATransition.bind(before.getValue("node-a"), afterB.getValue("node-a"), UNSELECTED, 100L))
        assertTrue(nodeATransition.advance(UNSELECTED, frameTime = 164L).animating)

        val afterC = entriesAfterSelection(
            selectedNode = "node-c",
            markers = tracker.record("selector", "node-b", "node-c", frameTime = 164L),
        )
        assertEquals("node A stays unchanged so ListAdapter does not rebind it", afterB["node-a"], afterC["node-a"])
        assertTrue("node A's existing transition continues", nodeATransition.advance(UNSELECTED, 180L).animating)

        val nodeBTransition = ProxySelectionColorTransition().apply {
            snap(UNSELECTED, frameTime = 100L)
        }
        assertTrue(nodeBTransition.bind(before.getValue("node-b"), afterB.getValue("node-b"), SELECTED, 100L))
        assertTrue(nodeBTransition.advance(SELECTED, frameTime = 164L).animating)
        assertTrue(nodeBTransition.bind(afterB.getValue("node-b"), afterC.getValue("node-b"), UNSELECTED, 164L))
        assertTrue(nodeBTransition.advance(UNSELECTED, frameTime = 180L).animating)

        val nodeCTransition = ProxySelectionColorTransition().apply {
            snap(UNSELECTED, frameTime = 100L)
        }
        assertTrue(nodeCTransition.bind(before.getValue("node-c"), afterC.getValue("node-c"), SELECTED, 180L))
        assertTrue(nodeCTransition.advance(SELECTED, frameTime = 196L).animating)
    }

    private fun entriesAfterSelection(selectedNode: String, markers: Map<String, Map<String, Long>>) =
        listOf("node-a", "node-b", "node-c").associateWith { name ->
            entry(name, selected = name == selectedNode, markers = markers)
        }

    private fun entry(name: String, selected: Boolean, markers: Map<String, Map<String, Long>>) =
        ProxyDisplayEntry(
            name = name,
            type = "Vless",
            delayText = "",
            delayMillis = null,
            isSelected = selected,
            animateSelectionChange = markers["selector"]?.containsKey(name) == true,
            selectionAnimationExpiresAt = markers["selector"]?.get(name),
        )

    private companion object {
        const val SELECTED = 0xff214372.toInt()
        const val UNSELECTED = 0xffffffff.toInt()
    }
}
