package rs.chimera.android.ui.metacubex.design

import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.view.ActivityBarLayout

@RunWith(AndroidJUnit4::class)
class ProvidersToolbarTouchInstrumentedTest {
    @Test
    fun toolbarConsumesBackgroundTouchesButStillDispatchesItsActions() = onMainThread {
        val fixture = Fixture()
        assertTrue(fixture.toolbar is ActivityBarLayout)

        fixture.tap(fixture.toolbar.width / 2f, fixture.toolbar.height / 2f)
        assertEquals("Toolbar background touches must not reach the covered list", 0, fixture.listTouches)

        listOf(R.id.providers_back, R.id.providers_update_all).forEach { id ->
            val action = fixture.root.findViewById<View>(id)
            var actionClicks = 0
            action.setOnClickListener { actionClicks++ }
            fixture.tap(action)
            assertEquals("Visible toolbar actions must remain clickable", 1, actionClicks)
        }

        fixture.tap(fixture.root.width / 2f, fixture.toolbar.bottom + 20f)
        assertEquals("The list remains interactive below the toolbar", 1, fixture.listTouches)
    }

    private fun onMainThread(block: () -> Unit) {
        var failure: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                block()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }

    @SuppressLint("ClickableViewAccessibility")
    private class Fixture {
        private val context = ContextThemeWrapper(
            InstrumentationRegistry.getInstrumentation().targetContext,
            R.style.Theme_Chimera_MetaCubeX,
        )
        val root = LayoutInflater.from(context)
            .inflate(R.layout.meta_design_providers, null, false) as ViewGroup
        val toolbar = root.findViewById<ActivityBarLayout>(R.id.providers_toolbar)
        private val list = root.findViewById<View>(R.id.providers_list)
        var listTouches = 0
            private set

        init {
            list.isClickable = true
            list.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) listTouches++
                true
            }
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }

        fun tap(target: View) {
            val bounds = Rect().also {
                target.getDrawingRect(it)
                root.offsetDescendantRectToMyCoords(target, it)
            }
            tap(bounds.exactCenterX(), bounds.exactCenterY())
        }

        fun tap(x: Float, y: Float) {
            val downTime = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
                val event = MotionEvent.obtain(downTime, downTime + index, action, x, y, 0)
                try {
                    assertTrue("The toolbar or list must handle this touch", root.dispatchTouchEvent(event))
                } finally {
                    event.recycle()
                }
            }
        }
    }
}
