package rs.chimera.android.ui.metacubex.design

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.metacubex.adapter.AccessControlAdapter
import rs.chimera.android.viewmodel.AppFilterMode

@RunWith(AndroidJUnit4::class)
class AccessControlDesignSelectionModeInstrumentedTest {
    @Test
    fun selectionAvailabilityUpdatesWhenModeChangesWithoutDataChanges() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: AccessControlDesign
            val apps = listOf(
                AccessControlDesign.InstalledApp(
                    packageName = "example.test",
                    label = "Test App",
                    icon = ColorDrawable(Color.BLUE),
                    system = false,
                    installTime = 1L,
                    updateTime = 1L,
                ),
            )

            scenario.onActivity { activity ->
                design = AccessControlDesign(activity)
                activity.setContentView(design.root)
                design.render(state(AppFilterMode.ALL, apps))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertSelectionEnabled(design, expected = false)
                design.render(state(AppFilterMode.ALLOWED, apps))
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertSelectionEnabled(design, expected = true)
                design.render(state(AppFilterMode.DISALLOWED, apps))
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertSelectionEnabled(design, expected = true)
                design.render(state(AppFilterMode.ALL, apps))
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertSelectionEnabled(design, expected = false)
            }
        }
    }

    private fun assertSelectionEnabled(
        design: AccessControlDesign,
        expected: Boolean,
    ) {
        val list = design.root.findViewById<RecyclerView>(R.id.app_list)
        val holder = list.findViewHolderForAdapterPosition(0) as AccessControlAdapter.ViewHolder
        assertTrue(holder.binding.root.isClickable == expected)
        assertTrue(holder.binding.root.isFocusable == expected)
        assertTrue(holder.binding.appSelected.isEnabled == expected)
    }

    private fun state(
        mode: AppFilterMode,
        apps: List<AccessControlDesign.InstalledApp>,
    ) = AccessControlDesign.State(
        mode = mode,
        sort = AccessControlDesign.Sort.NAME,
        reverse = false,
        apps = apps,
        selected = emptySet(),
        showSystemApps = false,
        query = "",
        loading = false,
        loadError = null,
        saving = false,
        dirty = false,
    )
}
