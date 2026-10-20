package rs.chimera.android

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.ui.metacubex.activity.MetaConnectionsActivity
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.metacubex.activity.MetaOverrideActivity
import rs.chimera.android.ui.metacubex.activity.MetaSettingsActivity
import rs.chimera.android.ui.metacubex.activity.MetaSettingsHomeActivity
import rs.chimera.android.ui.metacubex.design.ProxyDesign
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.backend.model.ProxySnapshot
import rs.chimera.android.ui.navigation.DefaultAppUiRouter
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.UiVariant

@RunWith(AndroidJUnit4::class)
class AppUiNavigationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context
        get() = instrumentation.targetContext
    private var originalPreferences: Map<String, Map<String, *>> = emptyMap()

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        originalPreferences = snapshotPreferences()
        clearPreferences()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Revoking this permission in @After can terminate the target process hosting the runner.
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @After
    fun tearDown() {
        resumedActivities().forEach { activity ->
            instrumentation.runOnMainSync(activity::finishAndRemoveTask)
        }
        restorePreferences()
    }

    @Test
    fun launcherDefaultsToWatfaqUi() {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

        assertNotNull(launchIntent)
        assertEquals(MainActivity::class.java.name, launchIntent?.component?.className)
        assertEquals(UiVariant.WATFAQ, AppPreferences.uiVariant(context))

        ActivityScenario.launch(MainActivity::class.java).use {
            waitForSingleActiveActivity<MainActivity>()
        }
    }

    @Test
    fun bothUiRootsCanSwitchRepeatedlyWithoutGrowingTheResumedStack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            var current: Activity = waitForResumedActivity<MainActivity>()

            repeat(UI_SWITCH_REPETITIONS) {
                instrumentation.runOnMainSync {
                    DefaultAppUiRouter.openMetaCubeX(current)
                }
                current = waitForSingleActiveActivity<MetaMainActivity>()
                assertEquals(UiVariant.METACUBEX, AppPreferences.uiVariant(context))

                instrumentation.runOnMainSync {
                    DefaultAppUiRouter.openWatfaq(current)
                }
                current = waitForSingleActiveActivity<MainActivity>()
                assertEquals(UiVariant.WATFAQ, AppPreferences.uiVariant(context))
            }
        }
    }

    @Test
    fun metaRootExposesMemoryTelemetry() {
        ActivityScenario.launch(MetaMainActivity::class.java).use {
            waitForResumedActivity<MetaMainActivity>()
            onView(withId(R.id.card_memory)).check(matches(isDisplayed()))
            onView(withId(R.id.text_memory)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun proxyPagerPagesFillTheViewport() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                design = ProxyDesign(activity)
                activity.addContentView(
                    design.root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                design.setGroups(
                    listOf(
                        ProxyGroupSnapshot(
                            name = "test-group",
                            proxies = listOf("test-node"),
                            selected = "test-node",
                            mode = ProxyMode.RULE,
                            proxyDetails = mapOf(
                                "test-group" to ProxySnapshot(
                                    name = "test-group",
                                    type = "Selector",
                                    history = emptyList(),
                                    isGroup = true,
                                    now = "test-node",
                                ),
                                "test-node" to ProxySnapshot(
                                    name = "test-node",
                                    type = "Vless",
                                    history = emptyList(),
                                ),
                            ),
                        ),
                    ),
                )
            }
            var measurement: Pair<IntArray, CharSequence?>? = null
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (measurement == null && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val viewPager = design.binding.viewPager
                    val pageRecycler = viewPager.getChildAt(0) as? RecyclerView ?: return@onActivity
                    val page = pageRecycler.findViewHolderForAdapterPosition(0)?.itemView
                        as? RecyclerView ?: return@onActivity
                    if (page.adapter?.itemCount == 1) {
                        val card = page.findViewHolderForAdapterPosition(0)?.itemView
                        if (card != null) {
                            measurement = Pair(
                                intArrayOf(viewPager.width, viewPager.height, page.width, page.height),
                                card.contentDescription,
                            )
                        }
                    }
                }
                if (measurement == null) SystemClock.sleep(50)
            }

            val rendered = measurement ?: throw AssertionError("Proxy item did not render")
            assertEquals(rendered.first[0], rendered.first[2])
            assertEquals(rendered.first[1], rendered.first[3])
            assertNotNull(rendered.second)

            var selectorClickable = false
            var selectorClickHandled = false
            scenario.onActivity {
                val viewPager = design.binding.viewPager
                val pageRecycler = viewPager.getChildAt(0) as RecyclerView
                val page = pageRecycler.findViewHolderForAdapterPosition(0)?.itemView as RecyclerView
                val card = page.findViewHolderForAdapterPosition(0)?.itemView
                    ?: throw AssertionError("Selector proxy item did not render")
                selectorClickable = card.isClickable
                selectorClickHandled = card.performClick()
            }
            assertTrue(selectorClickable)
            assertTrue(selectorClickHandled)
            assertEquals(
                ProxyDesign.Request.SelectProxy("test-group", "test-node"),
                design.requests.tryReceive().getOrNull(),
            )

            scenario.onActivity {
                design.setGroups(
                    listOf(
                        ProxyGroupSnapshot(
                            name = "test-url-test",
                            proxies = listOf("test-node"),
                            selected = "test-node",
                            mode = ProxyMode.RULE,
                            proxyDetails = mapOf(
                                "test-url-test" to ProxySnapshot(
                                    name = "test-url-test",
                                    type = "URLTest",
                                    history = emptyList(),
                                    isGroup = true,
                                    now = "test-node",
                                ),
                                "test-node" to ProxySnapshot(
                                    name = "test-node",
                                    type = "Vless",
                                    history = emptyList(),
                                ),
                            ),
                        ),
                    ),
                )
            }

            var nonSelectorClickable: Boolean? = null
            val nonSelectorDeadline = SystemClock.uptimeMillis() + 5_000
            while (
                nonSelectorClickable != false &&
                SystemClock.uptimeMillis() < nonSelectorDeadline
            ) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val viewPager = design.binding.viewPager
                    val pageRecycler = viewPager.getChildAt(0) as? RecyclerView ?: return@onActivity
                    val page = pageRecycler.findViewHolderForAdapterPosition(0)?.itemView
                        as? RecyclerView ?: return@onActivity
                    nonSelectorClickable = page.findViewHolderForAdapterPosition(0)?.itemView
                        ?.isClickable
                }
                if (nonSelectorClickable != false) SystemClock.sleep(50)
            }
            assertEquals(false, nonSelectorClickable)

            var nonSelectorClickHandled = true
            scenario.onActivity {
                val viewPager = design.binding.viewPager
                val pageRecycler = viewPager.getChildAt(0) as RecyclerView
                val page = pageRecycler.findViewHolderForAdapterPosition(0)?.itemView as RecyclerView
                val card = page.findViewHolderForAdapterPosition(0)?.itemView
                    ?: throw AssertionError("URLTest proxy item did not render")
                nonSelectorClickHandled = card.performClick()
            }
            assertFalse(nonSelectorClickHandled)
            assertFalse(design.requests.tryReceive().isSuccess)

            var stateVisibility = View.GONE
            var contentVisibility = View.VISIBLE
            scenario.onActivity {
                design.showError("Test error")
                stateVisibility = design.root.findViewById<View>(R.id.proxy_state).visibility
                contentVisibility = design.root.findViewById<View>(R.id.proxy_content).visibility
            }
            assertEquals(View.VISIBLE, stateVisibility)
            assertEquals(View.GONE, contentVisibility)
        }
    }

    @Test
    fun directModeRemainsSelectedWhenNoProxyPagesAreAvailable() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            var directModeChecked = false
            scenario.onActivity { activity ->
                val design = ProxyDesign(activity)
                activity.addContentView(
                    design.root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                design.setGroups(
                    listOf(
                        ProxyGroupSnapshot(
                            name = "DIRECT",
                            proxies = emptyList(),
                            selected = null,
                            mode = ProxyMode.DIRECT,
                            proxyDetails = mapOf(
                                "DIRECT" to ProxySnapshot(
                                    name = "DIRECT",
                                    type = "Direct",
                                    history = emptyList(),
                                ),
                            ),
                        ),
                    ),
                )
                directModeChecked = design.binding.toolbar.menu
                    .findItem(R.id.action_mode_direct)
                    .isChecked
            }

            assertTrue(directModeChecked)
        }
    }

    @Test
    fun metaConnectionsLayoutStartsWithCloseAllDisabled() {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_Chimera_MetaCubeX)
        val root = LayoutInflater.from(themedContext).inflate(R.layout.meta_design_connections, null)

        assertFalse(root.findViewById<android.view.View>(R.id.close_all).isEnabled)
    }

    @Test
    fun metaConnectionsEntryOpensConnectionsScreen() {
        ActivityScenario.launch(MetaMainActivity::class.java).use {
            waitForResumedActivity<MetaMainActivity>()
            onView(withId(R.id.card_connections)).perform(click())
            waitForResumedActivity<MetaConnectionsActivity>()
        }
    }

    @Test
    fun watfaqConnectionsEntryOpensConnectionsScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForResumedActivity<MainActivity>()
            composeRule.onNodeWithText(context.getString(R.string.stat_connections)).performClick()
            composeRule.onNodeWithText(context.getString(R.string.connections_title)).assertIsDisplayed()
        }
    }

    @Test
    fun metaSettingsEntryOpensCategoryHomeAndNetworkSettings() {
        ActivityScenario.launch(MetaMainActivity::class.java).use {
            waitForResumedActivity<MetaMainActivity>()
            onView(withId(R.id.card_settings)).perform(click())
            waitForResumedActivity<MetaSettingsHomeActivity>()
            onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
            onView(withId(R.id.category_app)).check(matches(isDisplayed()))
            onView(withId(R.id.category_network)).perform(click())
            waitForResumedActivity<MetaSettingsActivity>()
            onView(withId(R.id.card_ports)).check(matches(isDisplayed()))
            onView(withId(R.id.card_language)).check { view, _ ->
                assertFalse(view.isShown)
            }
        }
    }

    @Test
    fun metaSettingsMetaFeaturesCategoryShowsDiagnosticsOnly() {
        ActivityScenario.launch(MetaSettingsHomeActivity::class.java).use {
            waitForResumedActivity<MetaSettingsHomeActivity>()
            onView(withId(R.id.category_meta_features)).perform(click())
            waitForResumedActivity<MetaSettingsActivity>()
            onView(withId(R.id.card_dns)).check(matches(isDisplayed()))
            onView(withId(R.id.card_ports)).check { view, _ ->
                assertEquals(View.GONE, view.visibility)
            }
        }
    }

    @Test
    fun metaSettingsOverrideEntryOpensOverrideScreen() {
        ActivityScenario.launch(MetaSettingsHomeActivity::class.java).use {
            waitForResumedActivity<MetaSettingsHomeActivity>()
            onView(withId(R.id.category_override)).perform(click())
            waitForResumedActivity<MetaOverrideActivity>()
            onView(withText(R.string.meta_override_general)).check(matches(isDisplayed()))
            onView(withText(R.string.meta_override_http_port)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun watfaqSettingsEntryOpensSettingsScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForResumedActivity<MainActivity>()
            composeRule.onNodeWithText(context.getString(R.string.settings_screen)).performClick()
            composeRule.onNodeWithText(context.getString(R.string.settings_general))
                .assertIsDisplayed()
        }
    }

    @Test
    fun profileAndSettingsPersistAcrossActivityRecreation() {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(MIXED_PORT_KEY, TEST_MIXED_PORT)
            .putString(APP_FILTER_MODE_KEY, TEST_APP_FILTER_MODE)
            .commit()
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PROFILE_PATH_KEY, TEST_PROFILE_PATH)
            .commit()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForResumedActivity<MainActivity>()
            scenario.recreate()
            waitForResumedActivity<MainActivity>()

            val settings = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            val profiles = context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE)
            assertEquals(TEST_MIXED_PORT, settings.getInt(MIXED_PORT_KEY, 0))
            assertEquals(TEST_APP_FILTER_MODE, settings.getString(APP_FILTER_MODE_KEY, null))
            assertEquals(TEST_PROFILE_PATH, profiles.getString(PROFILE_PATH_KEY, null))
        }
    }

    private inline fun <reified T : Activity> waitForResumedActivity(): T {
        val deadline = SystemClock.uptimeMillis() + ACTIVITY_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            resumedActivities().filterIsInstance<T>().firstOrNull()?.let { return it }
            SystemClock.sleep(ACTIVITY_POLL_INTERVAL_MS)
        }
        throw AssertionError("${T::class.java.simpleName} did not reach RESUMED state")
    }

    private inline fun <reified T : Activity> waitForSingleActiveActivity(): T {
        val deadline = SystemClock.uptimeMillis() + ACTIVITY_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val activities = activeActivities()
            activities.singleOrNull()?.let { activity ->
                if (activity is T) return activity
            }
            SystemClock.sleep(ACTIVITY_POLL_INTERVAL_MS)
        }
        throw AssertionError("Activity stack did not settle on ${T::class.java.simpleName}")
    }

    private fun resumedActivities(): List<Activity> {
        var activities = emptyList<Activity>()
        instrumentation.runOnMainSync {
            activities = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .toList()
        }
        return activities
    }

    private fun activeActivities(): List<Activity> {
        var activities = emptyList<Activity>()
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            activities = ACTIVE_STAGES
                .flatMap(monitor::getActivitiesInStage)
                .distinct()
        }
        return activities
    }

    private fun clearPreferences() {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun snapshotPreferences(): Map<String, Map<String, *>> = listOf(
        SETTINGS_PREFS,
        PROFILE_PREFS,
    ).associateWith { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues { (_, value) ->
            if (value is Set<*>) value.toSet() else value
        }
    }

    private fun restorePreferences() {
        originalPreferences.forEach { (name, values) ->
            val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
            values.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    null -> editor.remove(key)
                }
            }
            check(editor.commit()) { "Failed to restore preferences '$name' after instrumentation test" }
        }
    }

    private companion object {
        val ACTIVE_STAGES = listOf(
            Stage.CREATED,
            Stage.STARTED,
            Stage.RESUMED,
            Stage.PAUSED,
            Stage.STOPPED,
        )
        const val UI_SWITCH_REPETITIONS = 10
        const val ACTIVITY_TIMEOUT_MS = 5_000L
        const val ACTIVITY_POLL_INTERVAL_MS = 50L
        const val SETTINGS_PREFS = "settings"
        const val PROFILE_PREFS = "file_prefs"
        const val MIXED_PORT_KEY = "mixed_port"
        const val APP_FILTER_MODE_KEY = "app_filter_mode"
        const val PROFILE_PATH_KEY = "profile_path"
        const val TEST_MIXED_PORT = 18_909
        const val TEST_APP_FILTER_MODE = "DISALLOWED"
        const val TEST_PROFILE_PATH = "/data/user/0/rs.chimera.android/files/test.yaml"
    }
}
