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
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
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
import rs.chimera.android.ui.metacubex.activity.MetaConnectionsActivity
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.metacubex.activity.MetaOverrideActivity
import rs.chimera.android.ui.metacubex.activity.MetaSettingsActivity
import rs.chimera.android.ui.metacubex.activity.MetaSettingsHomeActivity
import rs.chimera.android.ui.metacubex.design.ProxyCardView
import rs.chimera.android.ui.metacubex.design.ProxyDesign
import rs.chimera.android.ui.metacubex.design.ProxyDisplayPreferences
import rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry
import rs.chimera.android.ui.metacubex.design.isSelectionAnimationActive
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
                                    history = listOf(ProxyDelayHistory(delay = 0, time = 1L)),
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
                    val page = proxyPageRecycler(pageRecycler, 0) ?: return@onActivity
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
            assertEquals("test-node, Vless", rendered.second.toString())

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
            assertEquals(
                ProxyDesign.Request.SelectProxy("test-group", "test-node"),
                design.requests.tryReceive().getOrNull(),
            )

            var selectionContentAlpha = 0f
            var selectionPageInputEnabled = true
            scenario.onActivity {
                design.setSelecting(true)
                selectionContentAlpha = design.root.findViewById<View>(R.id.proxy_content).alpha
                selectionPageInputEnabled = design.binding.viewPager.isUserInputEnabled
                design.setSelecting(false)
            }
            assertEquals(1f, selectionContentAlpha, 0f)
            assertFalse(selectionPageInputEnabled)

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
            val direct = device.findObject(UiSelector().text(context.getString(R.string.cmfa_main_mode_direct)))
            val global = device.findObject(UiSelector().text(context.getString(R.string.cmfa_main_mode_global)))
            val rule = device.findObject(UiSelector().text(context.getString(R.string.cmfa_main_mode_rule)))

            assertTrue("Direct mode should be visible in the proxy menu", direct.exists())
            assertTrue("Global mode should be visible in the proxy menu", global.exists())
            assertTrue("Rule mode should be visible in the proxy menu", rule.exists())
            assertTrue(direct.visibleBounds.top < global.visibleBounds.top)
            assertTrue(global.visibleBounds.top < rule.visibleBounds.top)
            // UiAutomator exposes the row title as a plain TextView; inspect the MenuItem state directly.
            val proxyMenuDelegate = ProxyDesign::class.java.declaredFields
                .first { it.name.startsWith("proxyMenu") }
                .apply { isAccessible = true }
            val popupMenu = (proxyMenuDelegate.get(design) as Lazy<*>).value as androidx.appcompat.widget.PopupMenu
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
                design.binding.viewPager.setCurrentItem(1, false)
            }

            var selectionApplied = false
            var selectionRequest: ProxyDesign.Request? = null
            val clickDeadline = SystemClock.uptimeMillis() + 5_000
            while (!selectionApplied && SystemClock.uptimeMillis() < clickDeadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                        ?: return@onActivity
                    val page = proxyPageRecycler(pagerRecycler, 1) ?: return@onActivity
                    val card = page.findViewHolderForAdapterPosition(1)?.itemView as? ProxyCardView
                        ?: return@onActivity
                    if (card.performClick()) {
                        selectionRequest = design.requests.tryReceive().getOrNull()
                        selectionApplied = design.applyProxySelection("selector", "node-b")
                    }
                }
                if (!selectionApplied) SystemClock.sleep(50)
            }
            assertTrue("The requested selector should be present", selectionApplied)
            assertEquals(ProxyDesign.Request.SelectProxy("selector", "node-b"), selectionRequest)

            var selectedCardState: Pair<Boolean, Boolean>? = null
            var selectionChangeAnimated = false
            val selectionDeadline = SystemClock.uptimeMillis() + 5_000
            while (selectedCardState != Pair(false, true) && SystemClock.uptimeMillis() < selectionDeadline) {
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
                    selectionChangeAnimated = current.entry?.animateSelectionChange == true
                }
                if (selectedCardState != Pair(false, true)) SystemClock.sleep(50)
            }
            assertEquals(Pair(false, true), selectedCardState)
            assertTrue("A successful selector change should animate its selected card", selectionChangeAnimated)

            var linkedGroupLabel: CharSequence? = null
            scenario.onActivity { design.binding.viewPager.setCurrentItem(0, false) }
            val linkedLabelDeadline = SystemClock.uptimeMillis() + 5_000
            while (
                linkedGroupLabel?.contains("Selector(node-b)") != true &&
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
                if (linkedGroupLabel?.contains("Selector(node-b)") != true) SystemClock.sleep(50)
            }
            assertTrue(linkedGroupLabel.toString(), linkedGroupLabel?.contains("Selector(node-b)") == true)

            val confirmedSnapshots = snapshots.map { snapshot ->
                snapshot.copy(
                    selected = if (snapshot.name == "selector") "node-b" else snapshot.selected,
                    proxyDetails = snapshot.proxyDetails.mapValues { (name, proxy) ->
                        if (name == "selector") proxy.copy(now = "node-b") else proxy
                    },
                )
            }
            scenario.onActivity {
                design.binding.viewPager.setCurrentItem(1, false)
                design.setGroups(confirmedSnapshots)
            }
            var refreshedSelectionState: Pair<Boolean, Boolean>? = null
            var refreshRequestsAnimation = true
            val refreshDeadline = SystemClock.uptimeMillis() + 5_000
            while (
                (refreshedSelectionState != Pair(false, true) || refreshRequestsAnimation) &&
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
                    refreshRequestsAnimation = current.entry?.animateSelectionChange == true
                }
                if (refreshedSelectionState != Pair(false, true) || refreshRequestsAnimation) {
                    SystemClock.sleep(50)
                }
            }
            assertEquals(Pair(false, true), refreshedSelectionState)
            assertFalse("A confirmed list rebind should snap to its selection", refreshRequestsAnimation)
        }
    }

    @Test
    fun externalSelectionChangeAnimatesUntilSameSelectionRefreshConfirmsIt() {
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
            assertEquals(listOf(true, true), awaitProxySelectionMarkers(scenario, design, listOf(true, true)))

            scenario.onActivity { design.setGroups(listOf(changedSnapshot)) }
            assertEquals(listOf(false, false), awaitProxySelectionMarkers(scenario, design, listOf(false, false)))
        }
    }

    @Test
    fun cachedOffscreenPageDoesNotReplayExpiredSelectionAnimation() {
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
            assertTrue("The cached entry should retain its original marker and deadline", entry?.animateSelectionChange == true)
            assertFalse("The cached marker should be expired by first bind", entry?.isSelectionAnimationActive(SystemClock.uptimeMillis()) == true)
        }
    }

    @Test
    fun consecutiveProxySelectionsKeepEarlierNodesMarkedForTransition() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                design = createProxyDesign(
                    activity,
                    listOf(proxyGroupWithNodes("selector", "Selector", listOf("node-a", "node-b", "node-c"))),
                )
                assertTrue(design.applyProxySelection("selector", "node-b"))
                assertTrue(design.applyProxySelection("selector", "node-c"))
            }

            var transitionMarkers = emptyList<Boolean>()
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (transitionMarkers.size != 3 && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                        ?: return@onActivity
                    val page = proxyPageRecycler(pagerRecycler, 0) ?: return@onActivity
                    transitionMarkers = (0..2).mapNotNull { position ->
                        (page.findViewHolderForAdapterPosition(position)?.itemView as? ProxyCardView)
                            ?.entry
                            ?.animateSelectionChange
                    }
                }
                if (transitionMarkers.size != 3) SystemClock.sleep(50)
            }

            assertEquals(listOf(true, true, true), transitionMarkers)
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

            onView(withId(R.id.proxy_overflow_button)).perform(click())
            onView(withText(R.string.proxy_menu_mode)).perform(click())
            onView(withText(R.string.proxy_mode_rule)).perform(click())
        }
        assertEquals(
            ProxyDesign.Request.SwitchMode(ProxyMode.RULE),
            design.requests.tryReceive().getOrNull(),
        )
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
                    ),
                )
                assertEquals(2, design.binding.viewPager.adapter?.itemCount)
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

            scenario.onActivity { activity ->
                assertTrue(ProxyDisplayPreferences(activity).read().excludeNotSelectable)
                assertEquals(1, design.binding.viewPager.adapter?.itemCount)
                assertEquals(0, design.binding.viewPager.currentItem)
                assertEquals("自动测速", ProxyDisplayPreferences(activity).readLastGroupName())

                design.binding.proxyOverflowButton.performClick()
            }
            onView(withText(R.string.proxy_menu_filter)).perform(click())
            onView(withText(R.string.proxy_exclude_not_selectable)).perform(click())

            scenario.onActivity { activity ->
                assertFalse(ProxyDisplayPreferences(activity).read().excludeNotSelectable)
                assertEquals(2, design.binding.viewPager.adapter?.itemCount)
                assertEquals(1, design.binding.viewPager.currentItem)
                assertEquals("自动测速", ProxyDisplayPreferences(activity).readLastGroupName())
            }
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

    private fun proxyPageRecycler(pagerRecycler: RecyclerView, position: Int): RecyclerView? {
        val pageRoot = pagerRecycler.findViewHolderForAdapterPosition(position)?.itemView
            as? ViewGroup ?: return null
        return pageRoot.getChildAt(0) as? RecyclerView
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

    private fun awaitProxySelectionMarkers(
        scenario: ActivityScenario<MetaMainActivity>,
        design: ProxyDesign,
        expected: List<Boolean>,
    ): List<Boolean> {
        var markers = emptyList<Boolean>()
        val deadline = SystemClock.uptimeMillis() + 5_000
        while (markers != expected && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val pagerRecycler = design.binding.viewPager.getChildAt(0) as? RecyclerView
                    ?: return@onActivity
                val page = proxyPageRecycler(pagerRecycler, 0) ?: return@onActivity
                markers = (0..1).mapNotNull { position ->
                    (page.findViewHolderForAdapterPosition(position)?.itemView as? ProxyCardView)
                        ?.entry
                        ?.animateSelectionChange
                }
            }
            if (markers != expected) SystemClock.sleep(50)
        }
        return markers
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
