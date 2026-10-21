package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySelectionColorTransitionTest {
    @Test
    fun selectionAnimatesButConfirmedRefreshSnaps() {
        val previous = entry(name = "node-b", selected = false)
        val selectedAfterTap = entry(name = "node-b", selected = true, animate = true)
        val confirmedRefresh = entry(name = "node-b", selected = true)
        val transition = ProxySelectionColorTransition()

        transition.snap(UNSELECTED, frameTime = 100L)

        assertTrue(transition.bind(previous, selectedAfterTap, SELECTED, frameTime = 100L))
        val intermediate = transition.advance(SELECTED, frameTime = 164L)
        assertEquals(0xff000040.toInt(), intermediate.color)
        assertTrue(intermediate.animating)

        assertFalse(transition.bind(selectedAfterTap, confirmedRefresh, SELECTED, frameTime = 164L))
        val snapped = transition.advance(SELECTED, frameTime = 165L)
        assertEquals(SELECTED, snapped.color)
        assertFalse(snapped.animating)

        val recycled = entry(name = "node-a", selected = false, animate = true)
        val recycledTransition = ProxySelectionColorTransition()
        recycledTransition.snap(UNSELECTED, frameTime = 100L)
        assertTrue(recycledTransition.bind(previous, selectedAfterTap, SELECTED, frameTime = 101L))
        assertTrue(recycledTransition.advance(SELECTED, frameTime = 164L).animating)

        assertFalse(ProxySelectionTransitionPolicy.shouldAnimate(selectedAfterTap, recycled, frameTime = 165L))
        assertFalse(recycledTransition.bind(selectedAfterTap, recycled, UNSELECTED, frameTime = 165L))
        val recycledFrame = recycledTransition.advance(UNSELECTED, frameTime = 166L)
        assertEquals(UNSELECTED, recycledFrame.color)
        assertFalse(recycledFrame.animating)
    }

    @Test
    fun selectionUsesTimeSincePreviousDrawRatherThanTapTime() {
        val previous = entry(name = "node-b", selected = false)
        val selected = entry(name = "node-b", selected = true, animate = true)
        val transition = ProxySelectionColorTransition()

        transition.snap(UNSELECTED, frameTime = 100L)

        assertTrue(transition.bind(previous, selected, SELECTED, frameTime = 140L))
        val frame = transition.advance(SELECTED, frameTime = 164L)

        assertEquals(0xff000040.toInt(), frame.color)
        assertTrue(frame.animating)
    }

    @Test
    fun snapSetsInitialSelectionColorWithoutAnimation() {
        val transition = ProxySelectionColorTransition()

        transition.snap(UNSELECTED, frameTime = 100L)

        val frame = transition.advance(UNSELECTED, frameTime = 116L)
        assertEquals(UNSELECTED, frame.color)
        assertFalse(frame.animating)
    }

    @Test
    fun selectionChangeInterpolatesThenReachesTheTarget() {
        val transition = ProxySelectionColorTransition()
        transition.snap(UNSELECTED, frameTime = 100L)

        val intermediate = transition.advance(SELECTED, frameTime = 164L)
        assertEquals(0xff000040.toInt(), intermediate.color)
        assertNotEquals(SELECTED, intermediate.color)
        assertTrue(intermediate.animating)

        val completed = transition.advance(SELECTED, frameTime = 400L)
        assertEquals(SELECTED, completed.color)
        assertFalse(completed.animating)
    }

    @Test
    fun selectionAfterLongIdleUsesTheElapsedTimeSincePreviousDraw() {
        val previous = entry(name = "node-b", selected = false)
        val selected = entry(name = "node-b", selected = true, animate = true)
        val transition = ProxySelectionColorTransition()

        transition.snap(UNSELECTED, frameTime = 100L)

        assertTrue(transition.bind(previous, selected, SELECTED, frameTime = 60_000L))
        val intermediate = transition.advance(SELECTED, frameTime = 60_064L)

        assertEquals(SELECTED, intermediate.color)
        assertFalse(intermediate.animating)
    }

    @Test
    fun markedSelectionOnFirstBindSnapsToCurrentSelection() {
        val selected = entry(name = "node-b", selected = true, animate = true)
        val transition = ProxySelectionColorTransition()

        assertFalse(transition.bind(previous = null, current = selected, target = SELECTED, frameTime = 100L))

        val frame = transition.advance(SELECTED, frameTime = 164L)
        assertEquals(SELECTED, frame.color)
        assertFalse(frame.animating)
    }

    @Test
    fun sameNodeInAnotherGroupDoesNotContinueTheOldTransition() {
        val transition = ProxySelectionColorTransition()
        val previous = entry("node-a", selected = false).copy(selectionGroupName = "selector-a")
        val selected = previous.copy(isSelected = true, animateSelectionChange = true)
        val recycled = entry("node-a", selected = false).copy(selectionGroupName = "selector-b")
        transition.snap(UNSELECTED, frameTime = 100L)

        assertTrue(transition.bind(previous, selected, SELECTED, frameTime = 100L))
        assertTrue(transition.advance(SELECTED, frameTime = 164L).animating)
        assertFalse(transition.bind(selected, recycled, UNSELECTED, frameTime = 165L))

        val frame = transition.advance(UNSELECTED, frameTime = 166L)
        assertEquals(UNSELECTED, frame.color)
        assertFalse(frame.animating)
    }

    @Test
    fun delayedFirstBindDoesNotReplayAnActiveSelectionMarker() {
        val delayedEntry = entry(name = "node-b", selected = true, animate = true)
            .copy(selectionAnimationExpiresAt = 420L)
        val transition = ProxySelectionColorTransition()

        assertTrue(delayedEntry.isSelectionAnimationActive(frameTime = 419L))
        assertFalse(transition.bind(previous = null, current = delayedEntry, target = SELECTED, frameTime = 419L))
        assertEquals(SELECTED, transition.advance(SELECTED, frameTime = 420L).color)
        assertFalse(delayedEntry.isSelectionAnimationActive(frameTime = 420L))
    }

    @Test
    fun confirmationRebindKeepsAnInFlightSelectionTransition() {
        val previous = entry(name = "node-b", selected = false)
        val selected = entry(name = "node-b", selected = true, animate = true)
        val refreshed = selected.copy(delayText = "42")
        val transition = ProxySelectionColorTransition()
        transition.snap(UNSELECTED, frameTime = 100L)

        assertTrue(transition.bind(previous, selected, SELECTED, frameTime = 100L))
        assertTrue(transition.advance(SELECTED, frameTime = 116L).animating)
        assertTrue(transition.bind(selected, refreshed, SELECTED, frameTime = 132L))

        val continued = transition.advance(SELECTED, frameTime = 148L)
        assertNotEquals(SELECTED, continued.color)
        assertTrue(continued.animating)
    }

    private companion object {
        fun entry(name: String, selected: Boolean, animate: Boolean = false) =
            ProxyDisplayEntry(name, "VLESS", "", null, selected, animate)

        const val UNSELECTED = 0xff000000.toInt()
        const val SELECTED = 0xff0000ff.toInt()
    }
}
