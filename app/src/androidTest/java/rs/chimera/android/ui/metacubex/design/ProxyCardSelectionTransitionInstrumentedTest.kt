package rs.chimera.android.ui.metacubex.design

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R

@RunWith(AndroidJUnit4::class)
class ProxyCardSelectionTransitionInstrumentedTest {
    @Test
    fun selectionTransitionContinuesAcrossConfirmedRefresh() {
        val card = newCard()
        val transition = card.selectionTransition()
        val previous = entry("node-a", selected = false)
        val selected = previous.copy(isSelected = true)
        val confirmed = selected.copy(delayText = "42")
        val recycled = entry("node-b", selected = false)
        val selectedColor = card.backgroundColor(selected)
        val unselectedColor = card.backgroundColor(previous)
        val frameStart = System.currentTimeMillis()

        card.entry = previous
        transition.snap(unselectedColor, frameStart)
        card.bindEntry(selected)

        val firstFrame = transition.advance(selectedColor, frameStart + 64L)
        assertNotEquals(selectedColor, firstFrame.color)
        assertTrue(firstFrame.animating)

        card.entry = confirmed
        val confirmedFrame = transition.advance(selectedColor, frameStart + 80L)
        assertNotEquals(selectedColor, confirmedFrame.color)
        assertTrue(confirmedFrame.animating)

        val recycledColor = card.backgroundColor(recycled)
        card.entry = recycled
        val recycledFrame = transition.advance(recycledColor, frameStart + 81L)
        assertEquals(recycledColor, recycledFrame.color)
        assertFalse(recycledFrame.animating)
    }

    @Test
    fun firstBindSnapsToCurrentSelection() {
        val card = newCard()
        val transition = card.selectionTransition()
        val selected = entry("node-a", selected = true)
        val selectedColor = card.backgroundColor(selected)

        card.entry = selected

        val firstFrame = transition.advance(selectedColor, SystemClock.uptimeMillis() + 64L)
        assertEquals(selectedColor, firstFrame.color)
        assertFalse(firstFrame.animating)
    }

    @Test
    fun selectionTransitionDrawsAnIntermediateCardFill() {
        val card = newCard()
        val transition = card.selectionTransition()
        val previous = entry("node-a", selected = false)
        val selected = entry("node-a", selected = true)
        val selectedColor = card.backgroundColor(selected)
        val unselectedColor = card.backgroundColor(previous)

        card.entry = previous
        card.bindEntry(selected)
        setTransitionLastFrameTime(transition, System.currentTimeMillis() - 64L)
        card.measure(
            View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST),
        )
        card.layout(0, 0, card.measuredWidth, card.measuredHeight)
        val bitmap = Bitmap.createBitmap(card.measuredWidth, card.measuredHeight, Bitmap.Config.ARGB_8888)
        card.draw(Canvas(bitmap))

        val drawnFill = bitmap.getPixel(card.measuredWidth / 2, card.measuredHeight / 2)
        assertNotEquals("The drawn frame should leave the starting color", unselectedColor, drawnFill)
        assertNotEquals("The drawn frame should not already be the target color", selectedColor, drawnFill)
    }

    @Test
    fun columnChangeSnapsUnselectedCardToNewLayoutBackground() {
        val card = newCard()
        val transition = card.selectionTransition()
        val unselected = entry("node-a", selected = false)
        val selected = unselected.copy(isSelected = true)
        val unselectedColor = card.backgroundColor(unselected)
        val selectedColor = card.backgroundColor(selected)
        val frameStart = System.currentTimeMillis()

        card.entry = unselected
        transition.snap(unselectedColor, frameStart)
        transition.bind(
            unselected,
            selected,
            selectedColor,
            frameStart,
        )
        assertTrue(transition.advance(selectedColor, frameStart + 64L).animating)

        card.columns = 1

        assertEquals(Color.TRANSPARENT, card.backgroundColor(unselected))
        val frame = transition.advance(Color.TRANSPARENT, frameStart + 80L)
        assertEquals(Color.TRANSPARENT, frame.color)
        assertFalse(frame.animating)
    }

    @Test
    fun recycledCardDoesNotContinueAnimationAcrossSelectorGroups() {
        val card = newCard()
        val transition = card.selectionTransition()
        val previous = entry("node-a", selected = false, group = "selector-a")
        val selected = previous.copy(isSelected = true)
        val recycled = entry("node-a", selected = false, group = "selector-b")
        val selectedColor = card.backgroundColor(selected)
        val recycledColor = card.backgroundColor(recycled)
        val frameStart = System.currentTimeMillis()
        card.entry = previous
        transition.snap(card.backgroundColor(previous), frameStart)
        card.bindEntry(selected)
        assertTrue(transition.advance(selectedColor, frameStart + 64L).animating)

        card.entry = recycled
        val frame = transition.advance(recycledColor, frameStart + 65L)

        assertEquals(recycledColor, frame.color)
        assertFalse(frame.animating)
    }

    private fun newCard(): ProxyCardView = ProxyCardView(
        ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.Theme_Chimera_MetaCubeX,
        ),
    )

    private fun ProxyCardView.selectionTransition(): ProxySelectionColorTransition =
        ProxyCardView::class.java.getDeclaredField("selectionColorTransition")
            .apply { isAccessible = true }
            .get(this) as ProxySelectionColorTransition

    private fun setTransitionLastFrameTime(transition: ProxySelectionColorTransition, frameTime: Long) {
        ProxySelectionColorTransition::class.java.getDeclaredField("lastFrameTime")
            .apply { isAccessible = true }
            .setLong(transition, frameTime)
    }

    private fun ProxyCardView.backgroundColor(entry: ProxyDisplayEntry): Int =
        ProxyCardView::class.java.getDeclaredMethod("backgroundColor", ProxyDisplayEntry::class.java)
            .apply { isAccessible = true }
            .invoke(this, entry) as Int

    private fun entry(name: String, selected: Boolean, group: String = "selector") =
        ProxyDisplayEntry(
            name = name,
            subtitle = "Vless",
            delayText = "",
            delayMillis = null,
            isSelected = selected,
            selectionGroupName = group,
        )
}
