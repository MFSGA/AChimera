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
import rs.chimera.android.databinding.MetaDesignProxyBinding

@RunWith(AndroidJUnit4::class)
class ProxyToolbarTouchInstrumentedTest {
    @Test
    fun toolbarTitleSpacingAndInsetDoNotDispatchToNodesBehindThem() = onMainThread {
        val fixture = Fixture()
        val title = fixture.root.findViewById<View>(R.id.proxy_toolbar_title)
        val titleBounds = Rect().also {
            title.getDrawingRect(it)
            fixture.root.offsetDescendantRectToMyCoords(title, it)
        }

        fixture.tap(titleBounds.exactCenterX(), titleBounds.exactCenterY())
        fixture.tap(fixture.root.width / 2f, titleBounds.exactCenterY())
        fixture.tap(fixture.root.width / 2f, fixture.topInset / 2f)

        assertEquals("Toolbar touches must not reach covered proxy nodes", 0, fixture.nodeTouches)
    }

    @Test
    fun toolbarStillDispatchesToActionsAndNodesBelowRemainInteractive() = onMainThread {
        val fixture = Fixture()
        listOf(
            R.id.proxy_back_button,
            R.id.proxy_delay_test_icon,
            R.id.proxy_overflow_button,
        ).forEach { id ->
            val action = fixture.root.findViewById<View>(id)
            val bounds = Rect().also {
                action.getDrawingRect(it)
                fixture.root.offsetDescendantRectToMyCoords(action, it)
            }
            var actionTouches = 0
            action.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP) actionTouches++
                true
            }

            fixture.tap(bounds.exactCenterX(), bounds.exactCenterY())
            assertEquals("Toolbar actions must still receive touch events", 1, actionTouches)
        }

        assertEquals(0, fixture.nodeTouches)
        fixture.tap(fixture.root.width / 2f, fixture.root.height / 2f)
        assertEquals("Visible nodes below the toolbar must still receive touch events", 1, fixture.nodeTouches)
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
        private val binding = MetaDesignProxyBinding.inflate(LayoutInflater.from(context))
        val root = binding.root as ViewGroup
        val topInset = (24 * context.resources.displayMetrics.density).toInt()
        var nodeTouches = 0
            private set

        init {
            binding.viewPager.visibility = View.GONE
            binding.proxyState.visibility = View.GONE
            binding.fabDelayTest.visibility = View.GONE
            binding.proxyActivityBar.setPadding(0, topInset, 0, 0)
            // A scrolled proxy list extends behind the real toolbar. Use a touch
            // target there without starting a service or changing saved profiles.
            root.addView(
                View(context).apply {
                    setOnTouchListener { _, event ->
                        if (event.actionMasked == MotionEvent.ACTION_UP) nodeTouches++
                        true
                    }
                },
                0,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            root.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        }

        fun tap(x: Float, y: Float) {
            val downTime = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEachIndexed { index, action ->
                val event = MotionEvent.obtain(downTime, downTime + index, action, x, y, 0)
                try {
                    assertTrue("The toolbar or visible node should handle the touch", root.dispatchTouchEvent(event))
                } finally {
                    event.recycle()
                }
            }
        }
    }
}
