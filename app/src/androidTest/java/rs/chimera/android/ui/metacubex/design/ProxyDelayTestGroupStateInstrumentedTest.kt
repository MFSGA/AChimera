package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.backend.model.ProxySnapshot
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity

@RunWith(AndroidJUnit4::class)
class ProxyDelayTestGroupStateInstrumentedTest {
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
    fun delayTestProgressAndDisabledActionsAreScopedToCurrentGroup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                activity.getSharedPreferences("metacubex_ui", Context.MODE_PRIVATE).edit {
                    remove("proxy_last_group")
                }
                design = ProxyDesign(activity)
                activity.addContentView(
                    design.root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                design.setGroups(listOf(group("group-one", "node-one"), group("group-two", "node-two")))
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity {
                design.setDelayTestProgress("group-one", current = 1, total = 2)
                assertEquals(View.VISIBLE, design.binding.proxyDelayTestProgress.visibility)
                assertFalse(design.binding.proxyDelayTestIcon.isEnabled)
                assertFalse(design.binding.fabDelayTest.isEnabled)
            }

            scenario.onActivity { design.binding.viewPager.setCurrentItem(1, false) }
            instrumentation.waitForIdleSync()

            scenario.onActivity {
                assertEquals(View.GONE, design.binding.proxyDelayTestProgress.visibility)
                assertEquals(View.VISIBLE, design.binding.proxyDelayTestIcon.visibility)
                assertTrue(design.binding.proxyDelayTestIcon.isEnabled)
                assertTrue(design.binding.fabDelayTest.isEnabled)
                assertEquals(
                    it.getString(R.string.proxy_delay_test),
                    design.binding.fabDelayTest.contentDescription.toString(),
                )
                design.binding.fabDelayTest.performClick()
                assertEquals(
                    ProxyDesign.Request.DelayTest("group-two", listOf("node-two")),
                    design.requests.tryReceive().getOrNull(),
                )

                design.setDelayTestProgress("group-two", current = 1, total = 1)
                design.finishDelayTest("group-one")
                assertEquals(View.VISIBLE, design.binding.proxyDelayTestProgress.visibility)
                assertFalse(design.binding.proxyDelayTestIcon.isEnabled)

                design.finishDelayTest("group-two")
                assertEquals(View.GONE, design.binding.proxyDelayTestProgress.visibility)
                assertTrue(design.binding.proxyDelayTestIcon.isEnabled)
                assertTrue(design.binding.fabDelayTest.isEnabled)
            }
        }
    }

    private fun group(groupName: String, nodeName: String) = ProxyGroupSnapshot(
        name = groupName,
        proxies = listOf(nodeName),
        selected = nodeName,
        mode = ProxyMode.RULE,
        proxyDetails = mapOf(
            groupName to ProxySnapshot(
                name = groupName,
                type = "Selector",
                history = emptyList(),
                isGroup = true,
                now = nodeName,
            ),
            nodeName to ProxySnapshot(
                name = nodeName,
                type = "Vless",
                history = emptyList(),
            ),
        ),
    )

    private fun preferences() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences(PROXY_DISPLAY_PREFERENCES, Context.MODE_PRIVATE)

    private companion object {
        const val PROXY_DISPLAY_PREFERENCES = "metacubex_ui"
        const val PROXY_LAST_GROUP_KEY = "proxy_last_group"
    }
}
