package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySelectionColorTransitionTest {
    @Test
    fun successfulSelectionAnimatesAndConfirmedRefreshSnaps() {
        val previous = entry(name = "node-b", selected = false)
        val selectedAfterTap = entry(name = "node-b", selected = true, animate = true)
        val confirmedRefresh = entry(name = "node-b", selected = true)
        val recycled = entry(name = "node-a", selected = true, animate = true)
        val transition = ProxySelectionColorTransition()

        transition.snap(UNSELECTED, frameTime = 100L)

        assertTrue(transition.bind(previous, selectedAfterTap, SELECTED, frameTime = 100L))
        val intermediate = transition.advance(SELECTED, frameTime = 164L)
        assertEquals(0xff000040.toInt(), intermediate.color)
        assertTrue(intermediate.animating)

        assertNotEquals(selectedAfterTap, confirmedRefresh)
        assertFalse(transition.bind(selectedAfterTap, confirmedRefresh, SELECTED, frameTime = 164L))
        val snapped = transition.advance(SELECTED, frameTime = 165L)
        assertEquals(SELECTED, snapped.color)
        assertFalse(snapped.animating)

        assertFalse(ProxySelectionTransitionPolicy.shouldAnimate(previous, recycled))
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

    private companion object {
        fun entry(name: String, selected: Boolean, animate: Boolean = false) =
            ProxyDisplayEntry(name, "VLESS", "", null, selected, animate)

        const val UNSELECTED = 0xff000000.toInt()
        const val SELECTED = 0xff0000ff.toInt()
    }
}
