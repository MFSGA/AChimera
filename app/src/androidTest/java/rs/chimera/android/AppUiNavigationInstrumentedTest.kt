package rs.chimera.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.metacubex.activity.MetaOverrideActivity
import rs.chimera.android.ui.metacubex.activity.MetaSettingsActivity
import rs.chimera.android.ui.metacubex.activity.MetaSettingsHomeActivity
import rs.chimera.android.ui.metacubex.activity.runProxyModeSwitch
import rs.chimera.android.ui.metacubex.activity.runProxySelection
import rs.chimera.android.ui.metacubex.design.ProxyCardView
import rs.chimera.android.ui.metacubex.design.ProxyDesign
import rs.chimera.android.ui.metacubex.design.ProxyDisplayPreferences
import rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry
import rs.chimera.android.ui.metacubex.design.SettingsDesign
import rs.chimera.android.backend.model.ProxyDelayHistory
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
        assertEquals("rs.chimera.android.MainActivityAlias", launchIntent?.component?.className)
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
    fun metaRootOmitsEntriesNotPresentInReferenceHome() {
        ActivityScenario.launch(MetaMainActivity::class.java).use {
            waitForResumedActivity<MetaMainActivity>()
            onView(withText(R.string.connections_title)).check(doesNotExist())
            onView(withText(R.string.stat_memory)).check(doesNotExist())
        }
    }

    @Test
    fun metaHomeSwitchButtonOpensWatfaqAndPersistsTheSelection() {
        ActivityScenario.launch(MetaMainActivity::class.java).use {
            waitForResumedActivity<MetaMainActivity>()

            onView(withContentDescription(R.string.switch_to_watfaq_ui)).perform(click())

            waitForSingleActiveActivity<MainActivity>()
            assertEquals(UiVariant.WATFAQ, AppPreferences.uiVariant(context))
        }
    }

    @Test
    fun watfaqHomeSwitchButtonOpensMetaAndPersistsTheSelection() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForResumedActivity<MainActivity>()

            composeRule
                .onNodeWithContentDescription(context.getString(R.string.switch_to_metacubex_ui))
                .performClick()

            waitForSingleActiveActivity<MetaMainActivity>()
            assertEquals(UiVariant.METACUBEX, AppPreferences.uiVariant(context))
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
                                    history = listOf(ProxyDelayHistory(delay = 42, time = 1L)),
                                ),
                            ),
                        ),
                    ),
                )
            }
            var measurement: Pair<IntArray, CharSequence?>? = null
            var pagerOverlaysHeader = false
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (measurement == null && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    ViewCompat.dispatchApplyWindowInsets(
                        design.root,
                        WindowInsetsCompat.Builder()
                            .setInsets(
                                WindowInsetsCompat.Type.systemBars(),
                                Insets.of(
                                    0,
                                    design.root.findViewById<View>(R.id.proxy_activity_bar).paddingTop,
                                    0,
                                    28,
                                ),
                            )
                            .build(),
                    )
                    val viewPager = design.binding.viewPager
                    val pageRecycler = viewPager.getChildAt(0) as? RecyclerView ?: return@onActivity
                    val page = proxyPageRecycler(pageRecycler, 0) ?: return@onActivity
                    if (page.adapter?.itemCount == 1) {
                        val card = page.findViewHolderForAdapterPosition(0)?.itemView
                        val toolbar = design.root.findViewById<View>(R.id.proxy_activity_bar)
                        val tabs = design.root.findViewById<View>(R.id.tab_layout)
                        if (
                            card != null && toolbar.height > 0 && tabs.height > 0
                        ) {
                            val pagerLocation = IntArray(2)
                            val toolbarLocation = IntArray(2)
                            val tabsLocation = IntArray(2)
                            viewPager.getLocationOnScreen(pagerLocation)
                            toolbar.getLocationOnScreen(toolbarLocation)
                            tabs.getLocationOnScreen(tabsLocation)
                            pagerOverlaysHeader = pagerLocation[1] <= toolbarLocation[1] &&
                                pagerLocation[1] + viewPager.height >=
                                toolbarLocation[1] + toolbar.height &&
                                pagerLocation[1] <= tabsLocation[1] &&
                                pagerLocation[1] + viewPager.height >= tabsLocation[1] + tabs.height
                            measurement = Pair(
                                intArrayOf(
                                    viewPager.width,
                                    viewPager.height,
                                    page.width,
                                    page.height,
                                    page.paddingTop,
                                    toolbar.height,
                                    tabs.height,
                                ),
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
            assertTrue("Proxy pager should extend behind the toolbar and tabs", pagerOverlaysHeader)
            assertEquals(rendered.first[5] + rendered.first[6], rendered.first[4])
            assertNotNull(rendered.second)
            assertEquals("test-node, Vless, 42", rendered.second.toString())

            var selectorClickable = false
            var selectorClickHandled = false
            scenario.onActivity {
                val viewPager = design.binding.viewPager
                val pageRecycler = viewPager.getChildAt(0) as RecyclerView
                val page = proxyPageRecycler(pageRecycler, 0)
                    ?: throw AssertionError("Selector proxy page did not render")
                val card = page.findViewHolderForAdapterPosition(0)?.itemView
                    ?: throw AssertionError("Selector proxy item did not render")
                selectorClickable = card.isClickable
                selectorClickHandled = card.performClick()
            }
            assertTrue(selectorClickable)
            assertTrue(selectorClickHandled)
            assertProjectedSelectionRequest(
                design.requests.tryReceive().getOrNull(),
                "test-group",
                "test-node",
            )

            var nodeSelectableDuringDelayTest = false
            var modeMenuEnabledDuringDelayTest = false
            var delayActionsDisabledDuringDelayTest = false
            var delayProgressVisibleDuringDelayTest = false
            var delayTestSelectionRequest: ProxyDesign.Request? = null
            scenario.onActivity {
                design.setDelayTestProgress("test-group", 1, 2)
                val pagerRecycler = design.binding.viewPager.getChildAt(0) as RecyclerView
                val page = proxyPageRecycler(pagerRecycler, 0)
                    ?: throw AssertionError("Proxy page did not render during delay test")
                val card = page.findViewHolderForAdapterPosition(0)?.itemView
                    ?: throw AssertionError("Proxy item did not render during delay test")
                nodeSelectableDuringDelayTest =
                    design.binding.viewPager.isUserInputEnabled &&
                    card.isClickable &&
                    card.performClick()
                modeMenuEnabledDuringDelayTest = design.binding.proxyOverflowButton.isEnabled
                delayActionsDisabledDuringDelayTest =
                    !design.binding.fabDelayTest.isEnabled &&
                    !design.binding.proxyDelayTestIcon.isEnabled
                delayProgressVisibleDuringDelayTest =
                    design.binding.proxyDelayTestProgress.visibility == View.VISIBLE
                delayTestSelectionRequest = design.requests.tryReceive().getOrNull()
                design.finishDelayTest("test-group")
            }
            assertTrue("Node selection should stay available during delay tests", nodeSelectableDuringDelayTest)
            assertTrue("Proxy mode menu should stay available during delay tests", modeMenuEnabledDuringDelayTest)
            assertTrue("Delay-test actions should not start a duplicate test", delayActionsDisabledDuringDelayTest)
            assertTrue("Delay-test progress should be visible", delayProgressVisibleDuringDelayTest)
            assertProjectedSelectionRequest(delayTestSelectionRequest, "test-group", "test-node")

            var selectionPageInputEnabled = false
            var modeMenuEnabled = false
            var selectionDuringModeSwitch: ProxyDesign.Request? = null
            runBlocking {
                val modeSwitchStarted = CompletableDeferred<Unit>()
                val finishModeSwitch = CompletableDeferred<Unit>()
                val switch = async {
                    runProxyModeSwitch(
                        mode = ProxyMode.GLOBAL,
                        applyMode = {
                            modeSwitchStarted.complete(Unit)
                            finishModeSwitch.await()
                        },
                        currentMode = { ProxyMode.RULE },
                        setDisplayedMode = { mode ->
                            scenario.onActivity { design.setModeOverride(mode) }
                        },
                        refresh = {},
                        onFailure = { throw AssertionError("Unexpected mode switch failure", it) },
                    )
                }

                modeSwitchStarted.await()
                scenario.onActivity {
                    selectionPageInputEnabled = design.binding.viewPager.isUserInputEnabled
                    modeMenuEnabled = design.binding.proxyOverflowButton.isEnabled
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as RecyclerView
                    val page = proxyPageRecycler(pagerRecycler, 0)
                        ?: throw AssertionError("Proxy page did not render during mode switch")
                    val card = page.findViewHolderForAdapterPosition(0)?.itemView
                        ?: throw AssertionError("Proxy item did not render during mode switch")
                    if (card.performClick()) {
                        selectionDuringModeSwitch = design.requests.tryReceive().getOrNull()
                    }
                }
                finishModeSwitch.complete(Unit)
                switch.await()
            }
            assertTrue("Proxy groups should remain swipeable during mode changes", selectionPageInputEnabled)
            assertTrue("Proxy mode menu should remain enabled during mode changes", modeMenuEnabled)
            assertProjectedSelectionRequest(selectionDuringModeSwitch, "test-group", "test-node")

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
                    val page = proxyPageRecycler(pageRecycler, 0) ?: return@onActivity
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
                val page = proxyPageRecycler(pageRecycler, 0)
                    ?: throw AssertionError("URLTest proxy page did not render")
                val card = page.findViewHolderForAdapterPosition(0)?.itemView
                    ?: throw AssertionError("URLTest proxy item did not render")
                nonSelectorClickHandled = card.performClick()
            }
            assertFalse(nonSelectorClickHandled)
            assertFalse(design.requests.tryReceive().isSuccess)

            var errorStateVisibility = View.GONE
            var errorContentVisibility = View.VISIBLE
            var errorPagerVisibility = View.VISIBLE
            var recoveredStateVisibility = View.VISIBLE
            var recoveredContentVisibility = View.GONE
            var recoveredPagerVisibility = View.GONE
            var emptyVisibility = View.GONE
            var emptyPagerVisibility = View.VISIBLE
            scenario.onActivity {
                design.showError("Test error")
                errorStateVisibility = design.root.findViewById<View>(R.id.proxy_state).visibility
                errorContentVisibility = design.root.findViewById<View>(R.id.proxy_content).visibility
                errorPagerVisibility = design.binding.viewPager.visibility
                design.setGroups(listOf(proxyGroupWithNodes("recovered", "Selector", listOf("node"))))
                recoveredStateVisibility = design.root.findViewById<View>(R.id.proxy_state).visibility
                recoveredContentVisibility = design.root.findViewById<View>(R.id.proxy_content).visibility
                recoveredPagerVisibility = design.binding.viewPager.visibility
                design.setGroups(emptyList())
                emptyVisibility = design.root.findViewById<View>(R.id.proxy_empty_view).visibility
                emptyPagerVisibility = design.binding.viewPager.visibility
            }
            assertEquals(View.VISIBLE, errorStateVisibility)
            assertEquals(View.GONE, errorContentVisibility)
            assertEquals(View.GONE, errorPagerVisibility)
            assertEquals(View.GONE, recoveredStateVisibility)
            assertEquals(View.VISIBLE, recoveredContentVisibility)
            assertEquals(View.VISIBLE, recoveredPagerVisibility)
            assertEquals(View.VISIBLE, emptyVisibility)
            assertEquals(View.GONE, emptyPagerVisibility)
        }
    }

    @Test
    fun cachedProxyPagesReceiveUpdatedSystemBarInsets() {
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
                    (0..3).map { index ->
                        proxyGroupWithNodes("group-$index", "Selector", listOf("node-$index"))
                    },
                )
            }

            val firstInsets = WindowInsetsCompat.Builder()
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(0, 12, 0, 16),
                )
                .build()
            scenario.onActivity {
                ViewCompat.dispatchApplyWindowInsets(design.root, firstInsets)
            }
            awaitProxyPageBounds(scenario, design, 0, 1)
            lateinit var originalPage: RecyclerView
            scenario.onActivity {
                val pagerRecycler = design.binding.viewPager.getChildAt(0) as RecyclerView
                originalPage = proxyPageRecycler(pagerRecycler, 0)
                    ?: throw AssertionError("Initial proxy page did not render")
                design.binding.viewPager.setCurrentItem(3, false)
            }
            awaitProxyPageBounds(scenario, design, 3, 1)

            val updatedInsets = WindowInsetsCompat.Builder()
                .setInsets(
                    WindowInsetsCompat.Type.systemBars(),
                    Insets.of(0, 24, 0, 28),
                )
                .build()
            scenario.onActivity {
                ViewCompat.dispatchApplyWindowInsets(design.root, updatedInsets)
                design.binding.viewPager.setCurrentItem(0, false)
            }
            awaitProxyPageBounds(scenario, design, 0, 1)

            var pageInsets: IntArray? = null
            var restoredPage: RecyclerView? = null
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (pageInsets == null && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                        ?: return@onActivity
                    val page = proxyPageRecycler(pagerRecycler, 0) ?: return@onActivity
                    if (page.adapter?.itemCount == 1) {
                        restoredPage = page
                        pageInsets = intArrayOf(page.paddingTop, page.paddingBottom)
                    }
                }
                if (pageInsets == null) SystemClock.sleep(50)
            }

            val insets = pageInsets ?: throw AssertionError("Cached proxy page did not reattach")
            assertSame(originalPage, restoredPage)
            val expectedTopInset = 24 + context.resources.getDimensionPixelSize(
                R.dimen.metacubex_toolbar_height,
            ) + context.resources.getDimensionPixelSize(R.dimen.metacubex_tab_layout_height)
            assertEquals(expectedTopInset, insets[0])
            assertEquals(28, insets[1])
        }
    }

    @Test
    fun proxyModeMenuMatchesCmfaOrder() {
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
                val details = mapOf(
                    "selector" to ProxySnapshot(
                        name = "selector",
                        type = "Selector",
                        history = emptyList(),
                        isGroup = true,
                        now = "node",
                    ),
                    "node" to ProxySnapshot(name = "node", type = "Vless", history = emptyList()),
                )
                design.setGroups(
                    listOf(
                        ProxyGroupSnapshot(
                            name = "selector",
                            proxies = listOf("node"),
                            selected = "node",
                            mode = ProxyMode.RULE,
                            proxyDetails = details,
                        ),
                    ),
                )
                design.setModeOverride(ProxyMode.RULE)
            }

            instrumentation.waitForIdleSync()
            scenario.onActivity { design.binding.proxyOverflowButton.performClick() }
            instrumentation.waitForIdleSync()
            val device = UiDevice.getInstance(instrumentation)
            device.waitForIdle()
            val mode = device.findObject(UiSelector().text(context.getString(R.string.proxy_menu_mode)))
            assertTrue("Mode submenu should be visible in the proxy menu", mode.exists())
            mode.click()
            device.waitForIdle()
            val dontModify = device.findObject(
                UiSelector().text(context.getString(R.string.proxy_mode_dont_modify)),
            )
            val direct = device.findObject(UiSelector().text(context.getString(R.string.cmfa_main_mode_direct)))
            val global = device.findObject(UiSelector().text(context.getString(R.string.cmfa_main_mode_global)))
            val rule = device.findObject(UiSelector().text(context.getString(R.string.cmfa_main_mode_rule)))

            assertTrue("Do not modify should be visible in the proxy menu", dontModify.exists())
            assertTrue("Direct mode should be visible in the proxy menu", direct.exists())
            assertTrue("Global mode should be visible in the proxy menu", global.exists())
            assertTrue("Rule mode should be visible in the proxy menu", rule.exists())
            assertTrue(dontModify.visibleBounds.top < direct.visibleBounds.top)
            assertTrue(direct.visibleBounds.top < global.visibleBounds.top)
            assertTrue(global.visibleBounds.top < rule.visibleBounds.top)
            // UiAutomator exposes the row title as a plain TextView; inspect the MenuItem state directly.
            val proxyMenuDelegate = ProxyDesign::class.java.declaredFields
                .first { it.name.startsWith("proxyMenu") }
                .apply { isAccessible = true }
            val popupMenu = (proxyMenuDelegate.get(design) as Lazy<*>).value as androidx.appcompat.widget.PopupMenu
            assertFalse(popupMenu.menu.findItem(R.id.action_mode_dont_modify).isChecked)
            assertFalse(popupMenu.menu.findItem(R.id.action_mode_direct).isChecked)
            assertFalse(popupMenu.menu.findItem(R.id.action_mode_global).isChecked)
            assertTrue(popupMenu.menu.findItem(R.id.action_mode_rule).isChecked)
        }
    }

    @Test
    fun successfulProxySelectionImmediatelyUpdatesCardAndLinkedGroupLabel() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            lateinit var snapshots: List<ProxyGroupSnapshot>
            scenario.onActivity { activity ->
                design = ProxyDesign(activity)
                activity.addContentView(
                    design.root,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                val details = mapOf(
                    "outer" to ProxySnapshot(
                        name = "outer",
                        type = "Selector",
                        history = emptyList(),
                        isGroup = true,
                        now = "fallback",
                    ),
                    "selector" to ProxySnapshot(
                        name = "selector",
                        type = "Selector",
                        history = emptyList(),
                        isGroup = true,
                        now = "node-a",
                    ),
                    "fallback" to ProxySnapshot("fallback", "Direct", emptyList()),
                    "node-a" to ProxySnapshot("node-a", "Vless", emptyList()),
                    "node-b" to ProxySnapshot("node-b", "Vless", emptyList()),
                )
                snapshots =
                    listOf(
                        ProxyGroupSnapshot(
                            name = "outer",
                            proxies = listOf("selector", "fallback"),
                            selected = "fallback",
                            mode = ProxyMode.RULE,
                            proxyDetails = details,
                        ),
                        ProxyGroupSnapshot(
                            name = "selector",
                            proxies = listOf("node-a", "node-b"),
                            selected = "node-a",
                            mode = ProxyMode.RULE,
                            proxyDetails = details,
                        ),
                    )
                design.setGroups(snapshots)
            }

            awaitProxyPageSelection(
                scenario,
                design,
                pagePosition = 0,
                pageCount = 2,
                groupName = "outer",
                itemCount = 2,
            )
            scenario.onActivity { design.binding.viewPager.setCurrentItem(1, false) }
            awaitProxyPageSelection(
                scenario,
                design,
                pagePosition = 1,
                pageCount = 2,
                groupName = "selector",
                itemCount = 2,
            )

            var selectionRequested = false
            var selectionRequest: ProxyDesign.Request? = null
            val clickDeadline = SystemClock.uptimeMillis() + 5_000
            while (!selectionRequested && SystemClock.uptimeMillis() < clickDeadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                        ?: return@onActivity
                    val page = proxyPageRecycler(pagerRecycler, 1) ?: return@onActivity
                    val card = page.findViewHolderForAdapterPosition(1)?.itemView as? ProxyCardView
                        ?: return@onActivity
                    if (card.performClick()) {
                        selectionRequest = design.requests.tryReceive().getOrNull()
                        val request = selectionRequest as? ProxyDesign.Request.SelectProxy
                        selectionRequested = request?.let {
                            it.groupName == "selector" &&
                                it.proxyName == "node-b" &&
                                it.selectionProjected
                        } == true
                    }
                }
                if (!selectionRequested) SystemClock.sleep(50)
            }
            assertTrue("The requested selector should be present", selectionRequested)
            assertProjectedSelectionRequest(selectionRequest, "selector", "node-b")

            fun awaitSelectorCardState(expected: Pair<Boolean, Boolean>) {
                var state: Pair<Boolean, Boolean>? = null
                val deadline = SystemClock.uptimeMillis() + 5_000
                while (state != expected && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                            ?: return@onActivity
                        val page = proxyPageRecycler(pagerRecycler, 1) ?: return@onActivity
                        val previous = page.findViewHolderForAdapterPosition(0)?.itemView
                            as? ProxyCardView ?: return@onActivity
                        val current = page.findViewHolderForAdapterPosition(1)?.itemView
                            as? ProxyCardView ?: return@onActivity
                        state = (previous.entry?.isSelected == true) to
                            (current.entry?.isSelected == true)
                    }
                    if (state != expected) SystemClock.sleep(50)
                }
                assertEquals(expected, state)
            }

            fun awaitLinkedGroupLabel(expected: String) {
                var label: CharSequence? = null
                val deadline = SystemClock.uptimeMillis() + 5_000
                while (label?.contains(expected) != true && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        design.binding.viewPager.setCurrentItem(0, false)
                        val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                            ?: return@onActivity
                        val page = proxyPageRecycler(pagerRecycler, 0) ?: return@onActivity
                        label = page.findViewHolderForAdapterPosition(0)?.itemView?.contentDescription
                        design.binding.viewPager.setCurrentItem(1, false)
                    }
                    if (label?.contains(expected) != true) SystemClock.sleep(50)
                }
                assertTrue(
                    "Expected linked group label containing $expected, got $label",
                    label?.contains(expected) == true,
                )
            }

            awaitSelectorCardState(Pair(false, true))
            awaitLinkedGroupLabel("Selector(node-b)")

            runBlocking {
                val backendStarted = CompletableDeferred<Unit>()
                val finishBackend = CompletableDeferred<Unit>()
                val selection = async {
                    val request = selectionRequest as? ProxyDesign.Request.SelectProxy
                        ?: throw AssertionError("Expected a projected selector request")
                    runProxySelection(
                        selectionProjected = request.selectionProjected,
                        select = {
                            backendStarted.complete(Unit)
                            finishBackend.await()
                        },
                        confirmSelection = {
                            design.confirmProxySelection(
                                request.selectionToken,
                                request.groupName,
                                request.proxyName,
                            )
                        },
                        refresh = { throw AssertionError("successful selection should not refresh") },
                        onFailure = { throw AssertionError("unexpected selection error", it) },
                    )
                }

                backendStarted.await()
                awaitSelectorCardState(Pair(false, true))
                awaitLinkedGroupLabel("Selector(node-b)")

                var queuedSelection: ProxyDesign.Request? = null
                scenario.onActivity {
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                        ?: throw AssertionError("Proxy pager did not render during selection")
                    val page = proxyPageRecycler(pagerRecycler, 1)
                        ?: throw AssertionError("Proxy page did not render during selection")
                    val card = page.findViewHolderForAdapterPosition(0)?.itemView as? ProxyCardView
                        ?: throw AssertionError("Previously selected node did not render")
                    if (card.performClick()) {
                        queuedSelection = design.requests.tryReceive().getOrNull()
                    }
                }
                assertProjectedSelectionRequest(queuedSelection, "selector", "node-a")
                assertEquals(
                    listOf(true, false),
                    awaitProxySelectionStates(
                        scenario,
                        design,
                        listOf(true, false),
                        pagePosition = 1,
                    ),
                )
                awaitLinkedGroupLabel("Selector(node-a)")
                finishBackend.complete(Unit)
                selection.await()

                val secondRequest = queuedSelection as ProxyDesign.Request.SelectProxy
                var secondBackendCompleted = false
                runProxySelection(
                    selectionProjected = secondRequest.selectionProjected,
                    select = { secondBackendCompleted = true },
                    confirmSelection = {
                        design.confirmProxySelection(
                            secondRequest.selectionToken,
                            secondRequest.groupName,
                            secondRequest.proxyName,
                        )
                    },
                    refresh = { throw AssertionError("successful selection should not refresh") },
                    onFailure = { throw AssertionError("unexpected selection error", it) },
                )
                assertTrue(secondBackendCompleted)
            }

            var selectedCardState: Pair<Boolean, Boolean>? = null
            val selectionDeadline = SystemClock.uptimeMillis() + 5_000
            while (selectedCardState != Pair(true, false) && SystemClock.uptimeMillis() < selectionDeadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                        ?: return@onActivity
                    val page = proxyPageRecycler(pagerRecycler, 1) ?: return@onActivity
                    val previous = page.findViewHolderForAdapterPosition(0)?.itemView ?: return@onActivity
                    val current = page.findViewHolderForAdapterPosition(1)?.itemView as? ProxyCardView
                        ?: return@onActivity
                    selectedCardState =
                        ((previous as? ProxyCardView)?.entry?.isSelected == true) to
                            (current.entry?.isSelected == true)
                }
                if (selectedCardState != Pair(true, false)) SystemClock.sleep(50)
            }
            assertEquals(Pair(true, false), selectedCardState)

            var linkedGroupLabel: CharSequence? = null
            scenario.onActivity { design.binding.viewPager.setCurrentItem(0, false) }
            val linkedLabelDeadline = SystemClock.uptimeMillis() + 5_000
            while (
                linkedGroupLabel?.contains("Selector(node-a)") != true &&
                SystemClock.uptimeMillis() < linkedLabelDeadline
            ) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                        ?: return@onActivity
                    val page = proxyPageRecycler(pagerRecycler, 0) ?: return@onActivity
                    linkedGroupLabel = page.findViewHolderForAdapterPosition(0)?.itemView
                        ?.contentDescription
                }
                if (linkedGroupLabel?.contains("Selector(node-a)") != true) SystemClock.sleep(50)
            }
            assertTrue(linkedGroupLabel.toString(), linkedGroupLabel?.contains("Selector(node-a)") == true)

            scenario.onActivity {
                design.binding.viewPager.setCurrentItem(1, false)
                design.setGroups(snapshots)
            }
            var refreshedSelectionState: Pair<Boolean, Boolean>? = null
            val refreshDeadline = SystemClock.uptimeMillis() + 5_000
            while (
                refreshedSelectionState != Pair(true, false) &&
                SystemClock.uptimeMillis() < refreshDeadline
            ) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                        ?: return@onActivity
                    val page = proxyPageRecycler(pagerRecycler, 1) ?: return@onActivity
                    val previous = page.findViewHolderForAdapterPosition(0)?.itemView
                        ?: return@onActivity
                    val current = page.findViewHolderForAdapterPosition(1)?.itemView as? ProxyCardView
                        ?: return@onActivity
                    refreshedSelectionState =
                        ((previous as? ProxyCardView)?.entry?.isSelected == true) to
                            (current.entry?.isSelected == true)
                }
                if (refreshedSelectionState != Pair(true, false)) SystemClock.sleep(50)
            }
            assertEquals(Pair(true, false), refreshedSelectionState)
        }
    }

    @Test
    fun failedProxySelectionKeepsCardAndLinkedGroupLabelFromBackendSnapshot() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            val details = mapOf(
                "outer" to ProxySnapshot(
                    name = "outer",
                    type = "Selector",
                    history = emptyList(),
                    isGroup = true,
                    now = "fallback",
                ),
                "selector" to ProxySnapshot(
                    name = "selector",
                    type = "Selector",
                    history = emptyList(),
                    isGroup = true,
                    now = "node-a",
                ),
                "fallback" to ProxySnapshot("fallback", "Direct", emptyList()),
                "node-a" to ProxySnapshot("node-a", "Vless", emptyList()),
                "node-b" to ProxySnapshot("node-b", "Vless", emptyList()),
            )
            val backendSnapshot = listOf(
                ProxyGroupSnapshot(
                    name = "outer",
                    proxies = listOf("selector", "fallback"),
                    selected = "fallback",
                    mode = ProxyMode.RULE,
                    proxyDetails = details,
                ),
                ProxyGroupSnapshot(
                    name = "selector",
                    proxies = listOf("node-a", "node-b"),
                    selected = "node-a",
                    mode = ProxyMode.RULE,
                    proxyDetails = details,
                ),
            )
            scenario.onActivity { activity ->
                design = createProxyDesign(activity, backendSnapshot)
            }

            awaitProxyPageSelection(
                scenario,
                design,
                pagePosition = 0,
                pageCount = 2,
                groupName = "outer",
                itemCount = 2,
            )
            scenario.onActivity { design.binding.viewPager.setCurrentItem(1, false) }
            awaitProxyPageSelection(
                scenario,
                design,
                pagePosition = 1,
                pageCount = 2,
                groupName = "selector",
                itemCount = 2,
            )

            fun awaitSelectorCardState(expected: Pair<Boolean, Boolean>) {
                var state: Pair<Boolean, Boolean>? = null
                val deadline = SystemClock.uptimeMillis() + 5_000
                while (state != expected && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                            ?: return@onActivity
                        val page = proxyPageRecycler(pagerRecycler, 1) ?: return@onActivity
                        val previous = page.findViewHolderForAdapterPosition(0)?.itemView
                            as? ProxyCardView ?: return@onActivity
                        val current = page.findViewHolderForAdapterPosition(1)?.itemView
                            as? ProxyCardView ?: return@onActivity
                        state = (previous.entry?.isSelected == true) to
                            (current.entry?.isSelected == true)
                    }
                    if (state != expected) SystemClock.sleep(50)
                }
                assertEquals(expected, state)
            }

            fun awaitLinkedGroupLabel(expected: String) {
                var label: CharSequence? = null
                val deadline = SystemClock.uptimeMillis() + 5_000
                while (label?.contains(expected) != true && SystemClock.uptimeMillis() < deadline) {
                    instrumentation.waitForIdleSync()
                    scenario.onActivity {
                        design.binding.viewPager.setCurrentItem(0, false)
                        val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                            ?: return@onActivity
                        val page = proxyPageRecycler(pagerRecycler, 0) ?: return@onActivity
                        label = page.findViewHolderForAdapterPosition(0)?.itemView?.contentDescription
                        design.binding.viewPager.setCurrentItem(1, false)
                    }
                    if (label?.contains(expected) != true) SystemClock.sleep(50)
                }
                assertTrue(
                    "Expected linked group label containing $expected, got $label",
                    label?.contains(expected) == true,
                )
            }

            var selectionToken: Long? = null
            scenario.onActivity {
                selectionToken = design.applyProxySelection("selector", "node-b")
            }
            assertNotNull(selectionToken)
            var reportedError: Throwable? = null
            var refreshCount = 0
            runBlocking {
                runProxySelection(
                    selectionProjected = selectionToken != null,
                    select = {
                        awaitSelectorCardState(Pair(false, true))
                        awaitLinkedGroupLabel("Selector(node-b)")
                        throw IllegalStateException("backend rejected selection")
                    },
                    confirmSelection = {
                        design.confirmProxySelection(selectionToken, "selector", "node-b")
                    },
                    refresh = {
                        refreshCount++
                        scenario.onActivity { design.setGroups(backendSnapshot) }
                    },
                    onFailure = { error ->
                        reportedError = error
                        scenario.onActivity { design.rejectProxySelection(selectionToken) }
                    },
                )
            }

            assertEquals(IllegalStateException::class.java, reportedError?.javaClass)
            assertEquals(1, refreshCount)
            awaitSelectorCardState(Pair(true, false))
            awaitLinkedGroupLabel("Selector(node-a)")
        }
    }

    @Test
    fun failedSelectionAndRefreshRestoreTheLastConfirmedProxySnapshot() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                design = createProxyDesign(
                    activity,
                    listOf(proxyGroupWithNodes("selector", "Selector", listOf("node-a", "node-b"))),
                )
            }
            awaitProxyPageBounds(scenario, design, pagePosition = 0, itemCount = 2)

            var selectionToken: Long? = null
            scenario.onActivity {
                selectionToken = design.applyProxySelection("selector", "node-b")
            }
            assertNotNull(selectionToken)
            assertEquals(
                listOf(false, true),
                awaitProxySelectionStates(scenario, design, listOf(false, true)),
            )

            var reportedError: Throwable? = null
            runBlocking {
                runProxySelection(
                    selectionProjected = selectionToken != null,
                    select = { throw IllegalStateException("backend rejected selection") },
                    confirmSelection = {
                        design.confirmProxySelection(selectionToken, "selector", "node-b")
                    },
                    refresh = {
                        scenario.onActivity {
                            design.restoreConfirmedProxyGroups()
                            design.showError("Selection and refresh failed")
                        }
                    },
                    onFailure = { reportedError = it },
                )
            }
            assertEquals(IllegalStateException::class.java, reportedError?.javaClass)
            assertEquals(
                listOf(true, false),
                awaitProxySelectionStates(scenario, design, listOf(true, false)),
            )
        }
    }

    @Test
    fun externalSelectionChangeRemainsSelectedAfterUnchangedSnapshotRefresh() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            lateinit var changedSnapshot: ProxyGroupSnapshot
            scenario.onActivity { activity ->
                val initial = proxyGroupWithNodes("selector", "Selector", listOf("node-a", "node-b"))
                design = createProxyDesign(activity, listOf(initial))
                changedSnapshot = initial.copy(
                    selected = "node-b",
                    proxyDetails = initial.proxyDetails +
                        ("selector" to initial.proxyDetails.getValue("selector").copy(now = "node-b")),
                )
            }
            awaitProxyPageBounds(scenario, design, pagePosition = 0, itemCount = 2)

            scenario.onActivity { design.setGroups(listOf(changedSnapshot)) }
            assertEquals(listOf(false, true), awaitProxySelectionStates(scenario, design, listOf(false, true)))

            scenario.onActivity { design.setGroups(listOf(changedSnapshot)) }
            assertEquals(listOf(false, true), awaitProxySelectionStates(scenario, design, listOf(false, true)))
        }
    }

    @Test
    fun cachedOffscreenPageBindsTheLatestSelection() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            lateinit var updatedGroups: List<ProxyGroupSnapshot>
            scenario.onActivity { activity ->
                val initialGroups = (0..4).map { index ->
                    proxyGroupWithNodes("selector-$index", "Selector", listOf("node-a", "node-b"))
                }
                design = createProxyDesign(activity, initialGroups)
                updatedGroups = initialGroups.mapIndexed { index, snapshot ->
                    if (index != 4) {
                        snapshot
                    } else {
                        snapshot.copy(
                            selected = "node-b",
                            proxyDetails = snapshot.proxyDetails +
                                (snapshot.name to snapshot.proxyDetails.getValue(snapshot.name).copy(now = "node-b")),
                        )
                    }
                }
            }
            awaitProxyPageBounds(scenario, design, pagePosition = 0, itemCount = 2)
            var targetPageAdapterCreatedBeforeUpdate = true
            scenario.onActivity {
                targetPageAdapterCreatedBeforeUpdate = proxyPageAdapterExists(design, "selector-4")
            }
            assertFalse("The far-off selector page should not be bound initially", targetPageAdapterCreatedBeforeUpdate)

            scenario.onActivity { design.setGroups(updatedGroups) }
            SystemClock.sleep(400)
            var targetPageAdapterCreatedBeforeNavigation = true
            scenario.onActivity {
                targetPageAdapterCreatedBeforeNavigation = proxyPageAdapterExists(design, "selector-4")
            }
            assertFalse(
                "The far-off selector page should remain unbound until it is selected",
                targetPageAdapterCreatedBeforeNavigation,
            )

            scenario.onActivity { design.binding.viewPager.setCurrentItem(4, false) }
            awaitProxyPageBounds(scenario, design, pagePosition = 4, itemCount = 2)
            var targetPageAdapterCreatedAfterNavigation = false
            scenario.onActivity {
                targetPageAdapterCreatedAfterNavigation = proxyPageAdapterExists(design, "selector-4")
            }
            assertTrue("Selecting the far-off page should create its adapter", targetPageAdapterCreatedAfterNavigation)

            var delayedSelectionEntry: ProxyDisplayEntry? = null
            scenario.onActivity {
                val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                    ?: return@onActivity
                val page = proxyPageRecycler(pagerRecycler, 4) ?: return@onActivity
                delayedSelectionEntry = (page.findViewHolderForAdapterPosition(1)?.itemView as? ProxyCardView)
                    ?.entry
            }

            val entry = delayedSelectionEntry
            assertNotNull("The delayed selected node should be bound", entry)
            assertTrue("The delayed page should reflect the latest backend selection", entry?.isSelected == true)
        }
    }

    @Test
    fun consecutiveProxySelectionsKeepTheLatestNodeSelected() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                design = createProxyDesign(
                    activity,
                    listOf(proxyGroupWithNodes("selector", "Selector", listOf("node-a", "node-b", "node-c"))),
                )
                assertNotNull(design.applyProxySelection("selector", "node-b"))
                assertNotNull(design.applyProxySelection("selector", "node-c"))
            }

            assertEquals(listOf(false, false, true), awaitProxySelectionStates(scenario, design, listOf(false, false, true)))
        }
    }

    @Test
    fun proxyPageAllowsHorizontalPagingGesture() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                design = createProxyDesign(
                    activity,
                    listOf(
                        proxyGroup("first-selector", "Selector"),
                        proxyGroup("next-selector", "Selector"),
                    ),
                )
            }

            val pagerBounds = awaitProxyPageBounds(scenario, design, 0, 1).first
            var userInputEnabled = false
            scenario.onActivity { userInputEnabled = design.binding.viewPager.isUserInputEnabled }
            assertTrue(userInputEnabled)

            val horizontalY = pagerBounds[1] + pagerBounds[3] / 2f
            UiDevice.getInstance(instrumentation).swipe(
                (pagerBounds[0] + pagerBounds[2] * 0.8f).toInt(),
                horizontalY.toInt(),
                (pagerBounds[0] + pagerBounds[2] * 0.2f).toInt(),
                horizontalY.toInt(),
                24,
            )
            var currentPage = -1
            val deadline = SystemClock.uptimeMillis() + 3_000
            while (currentPage != 1 && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity { currentPage = design.binding.viewPager.currentItem }
                if (currentPage != 1) SystemClock.sleep(50)
            }
            assertEquals("A horizontal drag should page to the next proxy group", 1, currentPage)
        }
    }

    @Test
    fun proxyPagesShareRecycledViewPoolLikeCmfa() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                design = createProxyDesign(
                    activity,
                    listOf(
                        proxyGroup("first-selector", "Selector"),
                        proxyGroup("second-selector", "Selector"),
                    ),
                )
                design.binding.viewPager.offscreenPageLimit = 1
            }

            awaitProxyPageBounds(scenario, design, pagePosition = 0, itemCount = 1)
            awaitProxyPageBounds(scenario, design, pagePosition = 1, itemCount = 1)
            var firstPool: RecyclerView.RecycledViewPool? = null
            var secondPool: RecyclerView.RecycledViewPool? = null
            scenario.onActivity {
                val pagerRecycler = design.binding.viewPager.getChildAt(0) as RecyclerView
                firstPool = proxyPageRecycler(pagerRecycler, 0)?.recycledViewPool
                secondPool = proxyPageRecycler(pagerRecycler, 1)?.recycledViewPool
            }

            assertNotNull("The first proxy page should be attached", firstPool)
            assertNotNull("The second proxy page should be attached", secondPool)
            assertSame("CMFA shares one recycled-view pool across proxy pages", firstPool, secondPool)
        }
    }

    @Test
    fun proxyPageAllowsVerticalListScrollGesture() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            val nodeNames = (0 until 48).map { "scroll-node-$it" }
            scenario.onActivity { activity ->
                design = createProxyDesign(
                    activity,
                    listOf(proxyGroupWithNodes("long-selector", "Selector", nodeNames)),
                )
            }

            val listBounds = awaitProxyPageBounds(scenario, design, 0, nodeNames.size).second
            var offsetBefore = 0
            scenario.onActivity {
                val pagerRecycler = design.binding.viewPager.getChildAt(0) as RecyclerView
                offsetBefore = proxyPageRecycler(pagerRecycler, 0)
                    ?.computeVerticalScrollOffset() ?: 0
            }
            val centerX = (listBounds[0] + listBounds[2] / 2f).toInt()
            UiDevice.getInstance(instrumentation).swipe(
                centerX,
                (listBounds[1] + listBounds[3] * 0.8f).toInt(),
                centerX,
                (listBounds[1] + listBounds[3] * 0.3f).toInt(),
                24,
            )

            var scrollOffset = offsetBefore
            val deadline = SystemClock.uptimeMillis() + 3_000
            while (scrollOffset <= offsetBefore && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as RecyclerView
                    scrollOffset = proxyPageRecycler(pagerRecycler, 0)
                        ?.computeVerticalScrollOffset() ?: offsetBefore
                }
                if (scrollOffset <= offsetBefore) SystemClock.sleep(50)
            }
            assertTrue("A vertical drag should scroll the proxy list", scrollOffset > offsetBefore)
        }
    }

    @Test
    fun proxyModeMenuRemainsInteractiveWhenNoProxyPagesAreAvailable() {
        lateinit var design: ProxyDesign
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
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
            }

            val device = UiDevice.getInstance(instrumentation)
            scenario.onActivity { design.binding.proxyOverflowButton.performClick() }
            instrumentation.waitForIdleSync()
            device.waitForIdle()
            val modeMenu = device.findObject(
                UiSelector().text(context.getString(R.string.proxy_menu_mode)),
            )
            assertTrue("Proxy mode submenu should appear", modeMenu.waitForExists(5_000))
            modeMenu.click()
            device.waitForIdle()
            val ruleMode = device.findObject(
                UiSelector().text(context.getString(R.string.cmfa_main_mode_rule)),
            )
            assertTrue("Rule mode should appear in the submenu", ruleMode.waitForExists(5_000))
            val proxyMenuDelegate = ProxyDesign::class.java.declaredFields
                .first { it.name.startsWith("proxyMenu") }
                .apply { isAccessible = true }
            val popupMenu = (proxyMenuDelegate.get(design) as Lazy<*>).value as androidx.appcompat.widget.PopupMenu
            assertTrue(popupMenu.menu.findItem(R.id.action_mode_dont_modify).isChecked)
            ruleMode.click()
            assertEquals(
                ProxyDesign.Request.SwitchMode(ProxyMode.RULE),
                design.requests.tryReceive().getOrNull(),
            )
            scenario.onActivity {
                design.setModeOverride(ProxyMode.RULE)
                assertTrue(popupMenu.menu.findItem(R.id.action_mode_rule).isChecked)
                assertFalse(popupMenu.menu.findItem(R.id.action_mode_dont_modify).isChecked)
                design.binding.proxyOverflowButton.performClick()
            }

            instrumentation.waitForIdleSync()
            device.waitForIdle()
            val reopenedModeMenu = device.findObject(
                UiSelector().text(context.getString(R.string.proxy_menu_mode)),
            )
            assertTrue("Proxy mode submenu should reopen", reopenedModeMenu.waitForExists(5_000))
            reopenedModeMenu.click()
            device.waitForIdle()
            val dontModify = device.findObject(
                UiSelector().text(context.getString(R.string.proxy_mode_dont_modify)),
            )
            assertTrue("Do not modify should appear without proxy pages", dontModify.waitForExists(5_000))
            dontModify.click()
            assertEquals(
                ProxyDesign.Request.SwitchMode(null),
                design.requests.tryReceive().getOrNull(),
            )
            scenario.onActivity {
                design.setModeOverride(null)
                assertTrue(popupMenu.menu.findItem(R.id.action_mode_dont_modify).isChecked)
                assertFalse(popupMenu.menu.findItem(R.id.action_mode_rule).isChecked)
            }
        }
    }

    @Test
    fun failedProxyModeSwitchRestoresLastConfirmedMenuSelection() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            lateinit var popupMenu: androidx.appcompat.widget.PopupMenu
            scenario.onActivity { activity ->
                design = createProxyDesign(activity, emptyList())
                design.setModeOverride(ProxyMode.DIRECT)
                val proxyMenuDelegate = ProxyDesign::class.java.declaredFields
                    .first { it.name.startsWith("proxyMenu") }
                    .apply { isAccessible = true }
                popupMenu = (proxyMenuDelegate.get(design) as Lazy<*>).value as
                    androidx.appcompat.widget.PopupMenu

                // A checkable PopupMenu item is marked before the backend operation finishes.
                popupMenu.menu.findItem(R.id.action_mode_rule).isChecked = true
                assertTrue(popupMenu.menu.findItem(R.id.action_mode_rule).isChecked)

                // Re-applying the last confirmed mode must clear the transient menu check.
                design.setModeOverride(ProxyMode.DIRECT)
                assertTrue(popupMenu.menu.findItem(R.id.action_mode_direct).isChecked)
                assertFalse(popupMenu.menu.findItem(R.id.action_mode_rule).isChecked)
            }
        }
    }

    @Test
    fun proxyMenuFiltersAndRestoresNonSelectableGroups() {
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
                        proxyGroup("选择", "Selector"),
                        proxyGroup("自动测速", "URLTest"),
                        proxyGroup("备用", "Selector"),
                        proxyGroup("备用二", "Selector"),
                    ),
                )
                assertEquals(4, design.binding.viewPager.adapter?.itemCount)
            }

            scenario.onActivity {
                design.binding.viewPager.setCurrentItem(1, true)
            }
            instrumentation.waitForIdleSync()
            assertEquals(
                "自动测速",
                ProxyDisplayPreferences(context).readLastGroupName(),
            )

            scenario.onActivity {
                design.binding.proxyOverflowButton.performClick()
            }
            onView(withText(R.string.proxy_menu_filter)).perform(click())
            onView(withText(R.string.proxy_exclude_not_selectable)).perform(click())

            awaitProxyPageSelection(
                scenario,
                design,
                pagePosition = 0,
                pageCount = 3,
                groupName = "选择",
            )
            scenario.onActivity { activity ->
                assertTrue(ProxyDisplayPreferences(activity).read().excludeNotSelectable)
                assertEquals(3, design.binding.viewPager.adapter?.itemCount)
                assertEquals(0, design.binding.viewPager.currentItem)
                assertEquals("选择", ProxyDisplayPreferences(activity).readLastGroupName())

                design.binding.proxyOverflowButton.performClick()
            }
            onView(withText(R.string.proxy_menu_filter)).perform(click())
            onView(withText(R.string.proxy_exclude_not_selectable)).perform(click())

            awaitProxyPageSelection(
                scenario,
                design,
                pagePosition = 0,
                pageCount = 4,
                groupName = "选择",
            )
            scenario.onActivity { activity ->
                assertFalse(ProxyDisplayPreferences(activity).read().excludeNotSelectable)
                assertEquals(4, design.binding.viewPager.adapter?.itemCount)
                assertEquals(0, design.binding.viewPager.currentItem)
                assertEquals("选择", ProxyDisplayPreferences(activity).readLastGroupName())
            }
        }
    }

    @Test
    fun restoredProxyGroupSelectsAndRevealsItsTab() {
        val groups = (0..6).map { index ->
            proxyGroupWithNodes("restored-group-$index", "Selector", listOf("node-$index"))
        }
        val selectedPosition = groups.lastIndex

        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                ProxyDisplayPreferences(activity).updateLastGroupName(groups.last().name)
                design = createProxyDesign(activity, groups)
            }

            awaitProxyPageBounds(scenario, design, selectedPosition, itemCount = 1)

            var selectedTabVisible = false
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (!selectedTabVisible && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val tabLayout = design.binding.tabLayout
                    selectedTabVisible = design.binding.viewPager.currentItem == selectedPosition &&
                        tabLayout.selectedTabPosition == selectedPosition &&
                        isTabCenterVisible(tabLayout, selectedPosition)
                }
                if (!selectedTabVisible) SystemClock.sleep(50)
            }

            assertTrue(
                "Restored group tab at position $selectedPosition should be selected and visible",
                selectedTabVisible,
            )
        }
    }

    @Test
    fun metaConnectionsLayoutStartsWithCloseAllDisabled() {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_Chimera_MetaCubeX)
        val root = LayoutInflater.from(themedContext).inflate(R.layout.meta_design_connections, null)

        assertFalse(root.findViewById<android.view.View>(R.id.close_all).isEnabled)
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
            onView(withId(R.id.card_ports)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.card_language)).check { view, _ ->
                assertFalse(view.isShown)
            }
        }
    }

    @Test
    fun metaAppSettingsAutoRestartToggleUpdatesRecoveryPreference() {
        assertTrue(AppPreferences.autoRestart(context))
        val intent = Intent(context, MetaSettingsActivity::class.java)
            .putExtra(SettingsDesign.EXTRA_CATEGORY, SettingsDesign.Category.APP.name)

        ActivityScenario.launch<MetaSettingsActivity>(intent).use {
            waitForResumedActivity<MetaSettingsActivity>()
            onView(withId(R.id.switch_auto_restart)).check { view, _ ->
                assertTrue(
                    (view as com.google.android.material.switchmaterial.SwitchMaterial).isChecked,
                )
            }
            onView(withId(R.id.row_auto_restart)).perform(click())

            val deadline = SystemClock.uptimeMillis() + 3_000
            while (AppPreferences.autoRestart(context) && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(20)
            }
            assertFalse(AppPreferences.autoRestart(context))
            onView(withId(R.id.switch_auto_restart)).check { view, _ ->
                assertFalse(
                    (view as com.google.android.material.switchmaterial.SwitchMaterial).isChecked,
                )
            }
        }
    }

    @Test
    fun metaSettingsMetaFeaturesCategoryShowsUnavailableOptions() {
        ActivityScenario.launch(MetaSettingsHomeActivity::class.java).use {
            waitForResumedActivity<MetaSettingsHomeActivity>()
            onView(withId(R.id.category_meta_features)).perform(click())
            waitForResumedActivity<MetaSettingsActivity>()
            onView(withId(R.id.meta_features_content)).check { view, _ ->
                assertEquals(View.VISIBLE, view.visibility)
            }
            onView(withText(R.string.meta_feature_unified_delay)).check { view, _ ->
                val item = view.parent.parent as View
                assertFalse(item.isEnabled)
                assertFalse(item.isClickable)
                assertFalse(item.isFocusable)
                assertEquals(0.33f, item.alpha, 0.001f)
                assertEquals(
                    context.getString(R.string.meta_feature_unavailable_summary),
                    item.findViewById<android.widget.TextView>(R.id.meta_feature_summary)
                        .text.toString(),
                )
            }
            onView(withId(R.id.card_dns)).check { view, _ ->
                assertEquals(View.GONE, view.visibility)
            }
            onView(withId(R.id.card_ports)).check { view, _ ->
                assertEquals(View.GONE, view.visibility)
            }
        }
    }

    @Test
    fun metaSettingsAppCategoryKeepsDiagnosticsAvailable() {
        ActivityScenario.launch(MetaSettingsHomeActivity::class.java).use {
            waitForResumedActivity<MetaSettingsHomeActivity>()
            onView(withId(R.id.category_app)).perform(click())
            waitForResumedActivity<MetaSettingsActivity>()
            onView(withId(R.id.card_dns)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.card_rules)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.card_providers)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.meta_features_content)).check { view, _ ->
                assertFalse(view.isShown)
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
            onView(withText(R.string.settings_category_override_unavailable))
                .check(matches(isDisplayed()))
            onView(withText(R.string.meta_override_http_port)).check { view, _ ->
                var item: View = view
                while (item.id != R.id.meta_feature_preference_item) {
                    item = item.parent as? View
                        ?: error("Override preference row root not found")
                }
                assertFalse(item.isEnabled)
                assertFalse(item.isClickable)
                assertFalse(item.isFocusable)
                assertEquals(0.33f, item.alpha, 0.001f)
                assertEquals(
                    context.getString(R.string.meta_override_unavailable),
                    item.findViewById<android.widget.TextView>(R.id.meta_feature_summary)
                        .text.toString(),
                )
            }
            onView(withId(R.id.toolbar_reset)).check { view, _ ->
                assertFalse(view.isEnabled)
                assertFalse(view.isClickable)
                assertFalse(view.isFocusable)
                assertEquals(0.33f, view.alpha, 0.001f)
            }
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

    private fun proxyGroup(name: String, type: String) = ProxyGroupSnapshot(
        name = name,
        proxies = listOf("test-node"),
        selected = "test-node",
        mode = ProxyMode.RULE,
        proxyDetails = mapOf(
            name to ProxySnapshot(name, type, emptyList(), isGroup = true, now = "test-node"),
            "test-node" to ProxySnapshot("test-node", "Vless", emptyList()),
        ),
    )

    private fun proxyGroupWithNodes(
        name: String,
        type: String,
        nodeNames: List<String>,
    ): ProxyGroupSnapshot {
        val details = buildMap<String, ProxySnapshot> {
            put(
                name,
                ProxySnapshot(
                    name = name,
                    type = type,
                    history = emptyList(),
                    isGroup = true,
                    now = nodeNames.first(),
                ),
            )
            nodeNames.forEach { nodeName ->
                put(nodeName, ProxySnapshot(nodeName, "Vless", emptyList()))
            }
        }
        return ProxyGroupSnapshot(
            name = name,
            proxies = nodeNames,
            selected = nodeNames.first(),
            mode = ProxyMode.RULE,
            proxyDetails = details,
        )
    }

    private fun createProxyDesign(
        activity: Activity,
        groups: List<ProxyGroupSnapshot>,
    ): ProxyDesign = ProxyDesign(activity).also { design ->
        activity.addContentView(
            design.root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        design.setGroups(groups)
    }

    private fun awaitProxyPageBounds(
        scenario: ActivityScenario<MetaMainActivity>,
        design: ProxyDesign,
        pagePosition: Int,
        itemCount: Int,
    ): Pair<IntArray, IntArray> {
        var bounds: Pair<IntArray, IntArray>? = null
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (bounds == null && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val pager = design.binding.viewPager
                val pagerRecycler = pager.getChildAt(0) as? RecyclerView
                    ?: return@onActivity
                val list = proxyPageRecycler(pagerRecycler, pagePosition) ?: return@onActivity
                if (list.adapter?.itemCount == itemCount && list.height > 0) {
                    val pagerLocation = IntArray(2)
                    val listLocation = IntArray(2)
                    pager.getLocationOnScreen(pagerLocation)
                    list.getLocationOnScreen(listLocation)
                    bounds = Pair(
                        intArrayOf(pagerLocation[0], pagerLocation[1], pager.width, pager.height),
                        intArrayOf(listLocation[0], listLocation[1], list.width, list.height),
                    )
                }
            }
            if (bounds == null) SystemClock.sleep(50)
        }
        return bounds ?: throw AssertionError("Proxy pages and list did not render")
    }

    private fun awaitProxyPageSelection(
        scenario: ActivityScenario<MetaMainActivity>,
        design: ProxyDesign,
        pagePosition: Int,
        pageCount: Int,
        groupName: String,
        itemCount: Int = 1,
    ) {
        awaitProxyPageBounds(scenario, design, pagePosition, itemCount)
        var pageSelected = false
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (!pageSelected && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val tabLayout = design.binding.tabLayout
                pageSelected = design.binding.viewPager.adapter?.itemCount == pageCount &&
                    design.binding.viewPager.currentItem == pagePosition &&
                    tabLayout.selectedTabPosition == pagePosition &&
                    tabLayout.getTabAt(pagePosition)?.text == groupName &&
                    isTabCenterVisible(tabLayout, pagePosition) &&
                    ProxyDisplayPreferences(activity).readLastGroupName() == groupName
            }
            if (!pageSelected) SystemClock.sleep(50)
        }
        assertTrue("Proxy group '$groupName' should be restored on page $pagePosition", pageSelected)
    }

    private fun proxyPageRecycler(pagerRecycler: RecyclerView, position: Int): RecyclerView? {
        val pageRoot = pagerRecycler.findViewHolderForAdapterPosition(position)?.itemView
            as? ViewGroup ?: return null
        return pageRoot.getChildAt(0) as? RecyclerView
    }

    private fun isTabCenterVisible(tabLayout: TabLayout, position: Int): Boolean {
        val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return false
        val tabView = tabStrip.getChildAt(position) ?: return false
        if (tabView.width <= 0 || tabLayout.width <= 0) return false

        val tabLocation = IntArray(2)
        val layoutLocation = IntArray(2)
        tabView.getLocationOnScreen(tabLocation)
        tabLayout.getLocationOnScreen(layoutLocation)
        val tabCenter = tabLocation[0] + tabView.width / 2
        val viewportStart = layoutLocation[0] + tabLayout.paddingLeft
        val viewportEnd = layoutLocation[0] + tabLayout.width - tabLayout.paddingRight
        return tabCenter in viewportStart..viewportEnd
    }

    private fun proxyPageAdapterExists(design: ProxyDesign, groupName: String): Boolean {
        val pagerAdapterField = ProxyDesign::class.java.declaredFields
            .first { it.name == "pagerAdapter" }
            .apply { isAccessible = true }
        val pagerAdapter = pagerAdapterField.get(design) ?: return false
        val pageAdaptersField = pagerAdapter.javaClass.declaredFields
            .first { it.name == "pageAdapters" }
            .apply { isAccessible = true }
        val pageAdapters = pageAdaptersField.get(pagerAdapter) as? Map<*, *> ?: return false
        return pageAdapters.containsKey(groupName)
    }

    private fun awaitProxySelectionStates(
        scenario: ActivityScenario<MetaMainActivity>,
        design: ProxyDesign,
        expected: List<Boolean>,
        pagePosition: Int = 0,
    ): List<Boolean> {
        var selectionStates = emptyList<Boolean>()
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (selectionStates != expected && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                    ?: return@onActivity
                val page = proxyPageRecycler(pagerRecycler, pagePosition) ?: return@onActivity
                selectionStates = expected.indices.mapNotNull { position ->
                    (page.findViewHolderForAdapterPosition(position)?.itemView as? ProxyCardView)
                        ?.entry
                        ?.isSelected
                }
            }
            if (selectionStates != expected) SystemClock.sleep(50)
        }
        return selectionStates
    }

    private fun assertProjectedSelectionRequest(
        request: ProxyDesign.Request?,
        expectedGroup: String,
        expectedProxy: String,
    ) {
        val selection = request as? ProxyDesign.Request.SelectProxy
            ?: throw AssertionError("Expected a projected proxy selection request, got $request")
        assertEquals(expectedGroup, selection.groupName)
        assertEquals(expectedProxy, selection.proxyName)
        assertTrue(selection.selectionProjected)
    }

    private fun clearPreferences() {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PROFILE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PROXY_DISPLAY_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun snapshotPreferences(): Map<String, Map<String, *>> = listOf(
        SETTINGS_PREFS,
        PROFILE_PREFS,
        PROXY_DISPLAY_PREFS,
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
        const val PROXY_DISPLAY_PREFS = "metacubex_ui"
        const val MIXED_PORT_KEY = "mixed_port"
        const val APP_FILTER_MODE_KEY = "app_filter_mode"
        const val PROFILE_PATH_KEY = "profile_path"
        const val TEST_MIXED_PORT = 18_909
        const val TEST_APP_FILTER_MODE = "DISALLOWED"
        const val TEST_PROFILE_PATH = "/data/user/0/rs.chimera.android/files/test.yaml"
    }
}
