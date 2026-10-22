package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.edit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.backend.model.ProxySnapshot
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity

@RunWith(AndroidJUnit4::class)
class ProxyDesignEmptyStateInstrumentedTest {
    private var hadOriginalLastGroup = false
    private var originalLastGroup: String? = null

    @Before
    fun snapshotLastGroupPreference() {
        val preferences = preferences()
        hadOriginalLastGroup = preferences.contains(PROXY_LAST_GROUP_KEY)
        originalLastGroup = preferences.getString(PROXY_LAST_GROUP_KEY, null)
    }

    @After
    fun restoreLastGroupPreference() {
        preferences().edit {
            if (hadOriginalLastGroup) {
                putString(PROXY_LAST_GROUP_KEY, originalLastGroup)
            } else {
                remove(PROXY_LAST_GROUP_KEY)
            }
        }
    }

    @Test
    fun emptyGroupListMatchesReferenceAndDoesNotShowErrorActions() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                design = ProxyDesign(activity)
                activity.setContentView(design.root)
                design.setGroups(emptyList())
            }

            scenario.onActivity { activity ->
                val emptyView = design.root.findViewById<TextView>(R.id.proxy_empty_view)
                assertEquals(View.VISIBLE, emptyView.visibility)
                assertEquals(activity.getString(R.string.proxy_empty_tips), emptyView.text.toString())
                assertEquals(View.VISIBLE, design.root.findViewById<View>(R.id.proxy_activity_bar).visibility)
                assertEquals(View.GONE, design.root.findViewById<View>(R.id.proxy_state).visibility)
                assertEquals(View.GONE, design.root.findViewById<View>(R.id.proxy_content).visibility)
                assertEquals(View.GONE, design.root.findViewById<View>(R.id.proxy_delay_test_action).visibility)
                assertEquals(View.GONE, design.root.findViewById<View>(R.id.fab_delay_test).visibility)

                design.showError("test error", showRetry = false)
                assertEquals(View.GONE, emptyView.visibility)
                assertEquals(View.VISIBLE, design.root.findViewById<View>(R.id.proxy_state).visibility)

                design.setGroups(
                    listOf(
                        ProxyGroupSnapshot(
                            name = "test group",
                            proxies = listOf("test node"),
                            selected = "test node",
                            mode = ProxyMode.RULE,
                            proxyDetails = mapOf(
                                "test group" to ProxySnapshot(
                                    name = "test group",
                                    type = "Selector",
                                    history = emptyList(),
                                    isGroup = true,
                                    now = "test node",
                                ),
                                "test node" to ProxySnapshot(
                                    name = "test node",
                                    type = "test type",
                                    history = emptyList(),
                                ),
                            ),
                        ),
                    ),
                )
                assertEquals(View.GONE, emptyView.visibility)
                assertEquals(View.GONE, design.root.findViewById<View>(R.id.proxy_state).visibility)
                assertEquals(View.VISIBLE, design.root.findViewById<View>(R.id.proxy_content).visibility)
            }
        }
    }

    private fun preferences() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences(PROXY_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)

    private companion object {
        const val PROXY_DISPLAY_PREFERENCES = "metacubex_ui"
        const val PROXY_LAST_GROUP_KEY = "proxy_last_group"
    }
}
