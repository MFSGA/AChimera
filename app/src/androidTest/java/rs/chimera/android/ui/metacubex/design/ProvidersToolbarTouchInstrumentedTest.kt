package rs.chimera.android.ui.metacubex.design

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.metacubex.design.view.ActivityBarLayout

@RunWith(AndroidJUnit4::class)
class ProvidersToolbarTouchInstrumentedTest {
    @Test
    fun toolbarConsumesBackgroundTouchesButStillDispatchesItsActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var fixture: Fixture
            scenario.onActivity { activity ->
                fixture = Fixture(activity)
                activity.setContentView(fixture.root)
            }
            instrumentation.waitForIdleSync()
            assertTrue(fixture.toolbar is ActivityBarLayout)

            var toolbarCenter = 0 to 0
            scenario.onActivity { toolbarCenter = center(fixture.toolbar) }
            assertTrue(device.click(toolbarCenter.first, toolbarCenter.second))
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals("Toolbar background touches must not reach the covered list", 0, fixture.listTouches)
            }

            listOf(R.id.providers_back, R.id.providers_update_all).forEach { id ->
                val actionName = fixture.root.resources.getResourceEntryName(id)
                val actionClicks = AtomicInteger()
                scenario.onActivity {
                    fixture.root.findViewById<View>(id).setOnClickListener { actionClicks.incrementAndGet() }
                }
                onView(withId(id)).perform(click())
                assertEquals("Visible toolbar action $actionName must remain clickable", 1, actionClicks.get())
            }

            var belowToolbar = 0 to 0
            scenario.onActivity {
                val rootCenterX = center(fixture.root).first
                val toolbarLocation = IntArray(2)
                fixture.toolbar.getLocationOnScreen(toolbarLocation)
                val belowToolbarY = toolbarLocation[1] + fixture.toolbar.height + 20
                belowToolbar = rootCenterX to belowToolbarY
            }
            assertTrue(device.click(belowToolbar.first, belowToolbar.second))
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals("The list remains interactive below the toolbar", 1, fixture.listTouches)
            }
        }
    }

    private fun center(view: View): Pair<Int, Int> {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return location[0] + view.width / 2 to location[1] + view.height / 2
    }

    @SuppressLint("ClickableViewAccessibility")
    private class Fixture(context: Context) {
        private val themedContext = ContextThemeWrapper(context, R.style.Theme_Chimera_MetaCubeX)
        val root = LayoutInflater.from(themedContext)
            .inflate(R.layout.meta_design_providers, null, false) as View
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
        }
    }
}
