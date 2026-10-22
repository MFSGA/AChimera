package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySelectionColorTransitionTest {
    @Test
    fun selectionAnimationContinuesAcrossConfirmedRefresh() {
        val previous = entry(name = "node-b", selected = false)
        val selectedAfterTap = entry(name = "node-b", selected = true)
        val confirmedRefresh = selectedAfterTap.copy(delayText = "42")
        val transition = ProxySelectionColorTransition()

        transition.snap(UNSELECTED, frameTime = 100L)

        assertTrue(transition.bind(previous, selectedAfterTap, SELECTED, frameTime = 100L))
        val intermediate = transition.advance(SELECTED, frameTime = 164L)
        assertEquals(0xff000040.toInt(), intermediate.color)
        assertTrue(intermediate.animating)

        assertTrue(transition.bind(selectedAfterTap, confirmedRefresh, SELECTED, frameTime = 164L))
        val continued = transition.advance(SELECTED, frameTime = 180L)
        assertNotEquals(SELECTED, continued.color)
        assertTrue(continued.animating)

        val recycled = entry(name = "node-a", selected = false)
        val recycledTransition = ProxySelectionColorTransition()
        recycledTransition.snap(UNSELECTED, frameTime = 100L)
        assertTrue(recycledTransition.bind(previous, selectedAfterTap, SELECTED, frameTime = 101L))
        assertTrue(recycledTransition.advance(SELECTED, frameTime = 164L).animating)

        assertFalse(ProxySelectionTransitionPolicy.shouldAnimate(selectedAfterTap, recycled))
        assertFalse(recycledTransition.bind(selectedAfterTap, recycled, UNSELECTED, frameTime = 165L))
        val recycledFrame = recycledTransition.advance(UNSELECTED, frameTime = 166L)
        assertEquals(UNSELECTED, recycledFrame.color)
        assertFalse(recycledFrame.animating)
    }

    @Test
    fun selectionUsesTimeSincePreviousDrawRatherThanTapTime() {
        val previous = entry(name = "node-b", selected = false)
        val selected = entry(name = "node-b", selected = true)
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
        val selected = entry(name = "node-b", selected = true)
        val transition = ProxySelectionColorTransition()

        transition.snap(UNSELECTED, frameTime = 100L)

        assertTrue(transition.bind(previous, selected, SELECTED, frameTime = 60_000L))
        val intermediate = transition.advance(SELECTED, frameTime = 60_064L)

        assertEquals(SELECTED, intermediate.color)
        assertFalse(intermediate.animating)
    }

    @Test
    fun firstBindSnapsToCurrentSelection() {
        val selected = entry(name = "node-b", selected = true)
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
        val selected = previous.copy(isSelected = true)
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
    fun confirmationRebindKeepsAnInFlightSelectionTransition() {
        val previous = entry(name = "node-b", selected = false)
        val selected = entry(name = "node-b", selected = true)
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
        fun entry(name: String, selected: Boolean) =
            ProxyDisplayEntry(name, "VLESS", "", null, selected)

        const val UNSELECTED = 0xff000000.toInt()
        const val SELECTED = 0xff0000ff.toInt()
    }
}
