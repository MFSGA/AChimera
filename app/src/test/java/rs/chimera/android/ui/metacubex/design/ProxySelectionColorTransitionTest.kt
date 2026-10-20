package rs.chimera.android.ui.metacubex.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxySelectionColorTransitionTest {
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
        assertNotEquals(UNSELECTED, intermediate.color)
        assertNotEquals(SELECTED, intermediate.color)
        assertTrue(intermediate.animating)

        val completed = transition.advance(SELECTED, frameTime = 400L)
        assertEquals(SELECTED, completed.color)
        assertFalse(completed.animating)
    }

    private companion object {
        const val UNSELECTED = 0xff000000.toInt()
        const val SELECTED = 0xff0000ff.toInt()
    }
}
