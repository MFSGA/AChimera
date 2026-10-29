package rs.chimera.android.ui.metacubex.activity

import android.content.Context
import android.content.ContentValues
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.ProxyGroupSnapshot
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.backend.model.ProxySnapshot
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.ui.metacubex.design.ProxyCardView
import rs.chimera.android.ui.metacubex.design.ProxyDesign
import rs.chimera.android.ui.metacubex.design.ProxyDisplayPolicy
import rs.chimera.android.ui.metacubex.design.ProxySelectionColorTransition
import rs.chimera.android.ui.metacubex.design.showExceptionToast
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ProxySelectionRollbackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val preferences get() = instrumentation.targetContext.getSharedPreferences("metacubex_ui", Context.MODE_PRIVATE)
    private var originalLastGroup: String? = null
    private var hadLastGroup = false
    private var originalColumns = 2
    private var hadColumns = false

    @Before
    fun rememberDisplayPreferences() {
        hadLastGroup = preferences.contains("proxy_last_group")
        originalLastGroup = preferences.getString("proxy_last_group", null)
        hadColumns = preferences.contains("proxy_columns")
        originalColumns = preferences.getInt("proxy_columns", 2)
    }

    @Test
    fun selectionFailureShowsCmfaDetailsToastAndDialog() = withFixture { scenario, design ->
        val message = "selection failed"
        runBlocking { design.showExceptionToast(message) }

        onView(withText(message)).check(matches(isDisplayed()))
        capture(scenario, design, "selection-failure-toast")
        onView(withId(com.google.android.material.R.id.snackbar_action))
            .check(matches(withText(R.string.meta_error_details)))
            .perform(click())
        onView(withText(R.string.meta_error_title))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        onView(withText(message))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
        capture(scenario, design, "selection-failure-details")
        onView(withId(android.R.id.button1))
            .inRoot(isDialog())
            .perform(click())
    }

    @After
    fun restoreDisplayPreferences() {
        preferences.edit(commit = true) {
            if (hadLastGroup) putString("proxy_last_group", originalLastGroup) else remove("proxy_last_group")
            if (hadColumns) putInt("proxy_columns", originalColumns) else remove("proxy_columns")
        }
    }

    @Test
    fun rejectedProjectionRestoresTheVisibleNodeAndLinkedParentWithoutAnimation() = withFixture { scenario, design ->
        awaitCard(scenario, design, OLD_NODE, selected = true)
        capture(scenario, design, "fixture-before")
        lateinit var rejectedRequest: ProxyDesign.Request.SelectProxy
        scenario.onActivity {
            assertTrue(card(design, NEW_NODE)!!.performClick())
            rejectedRequest = assertProjectedSelectionRequest(
                design.requests.tryReceive().getOrNull(),
                NEW_NODE,
            )
            assertTrue(card(design, NEW_NODE)!!.entry!!.isSelected)
            assertFalse(card(design, OLD_NODE)!!.entry!!.isSelected)
        }
        capture(scenario, design, "fixture-projected")
        scenario.onActivity {
            design.rejectProxySelection(rejectedRequest.selectionToken)
            assertSnapped(card(design, OLD_NODE)!!, selected = true)
            assertSnapped(card(design, NEW_NODE)!!, selected = false)
        }
        awaitCard(scenario, design, OLD_NODE, selected = true)
        capture(scenario, design, "fixture-restored")
        scenario.onActivity { design.binding.viewPager.setCurrentItem(1, false) }
        awaitCard(scenario, design, MANUAL, selected = true, subtitle = "Selector($OLD_NODE)")
        capture(scenario, design, "fixture-parent-restored")
    }

    @Test
    fun confirmedSelectionIsNotLostWhenALaterProjectedSelectionFails() = withFixture { scenario, design ->
        awaitCard(scenario, design, OLD_NODE, selected = true)
        scenario.onActivity {
            assertTrue(card(design, NEW_NODE)!!.performClick())
            val firstRequest = requireSelectionRequest(design.requests.tryReceive().getOrNull())
            assertTrue(design.confirmProxySelection(firstRequest.selectionToken, MANUAL, NEW_NODE))
            assertTrue(card(design, OLD_NODE)!!.performClick())
            val rejectedRequest = assertProjectedSelectionRequest(
                design.requests.tryReceive().getOrNull(),
                OLD_NODE,
            )
            design.rejectProxySelection(rejectedRequest.selectionToken)
            assertSnapped(card(design, NEW_NODE)!!, selected = true)
            assertSnapped(card(design, OLD_NODE)!!, selected = false)
        }
        awaitCard(scenario, design, NEW_NODE, selected = true)
        scenario.onActivity { design.binding.viewPager.setCurrentItem(1, false) }
        awaitCard(scenario, design, MANUAL, selected = true, subtitle = "Selector($NEW_NODE)")
    }

    @Test
    fun queuedConfirmationsKeepTheLatestProjectionUntilTheFinalRejection() = withFixture { scenario, design ->
        awaitCard(scenario, design, OLD_NODE, selected = true)
        capture(scenario, design, "queue-fixture-before")
        lateinit var requests: List<ProxyDesign.Request.SelectProxy>
        scenario.onActivity {
            listOf(NEW_NODE, OLD_NODE, NEW_NODE, OLD_NODE).forEach { name ->
                assertTrue(card(design, name)!!.performClick())
                assertTrue(card(design, name)!!.entry!!.isSelected)
            }
            requests = List(4) { requireSelectionRequest(design.requests.tryReceive().getOrNull()) }
            assertEquals(listOf(NEW_NODE, OLD_NODE, NEW_NODE, OLD_NODE), requests.map { it.proxyName })
            // Earlier confirmations must not repaint the newer queued projection.
            assertTrue(design.confirmProxySelection(requests[0].selectionToken, MANUAL, NEW_NODE))
            assertTrue(card(design, OLD_NODE)!!.entry!!.isSelected)
            assertTrue(design.confirmProxySelection(requests[2].selectionToken, MANUAL, NEW_NODE))
            assertTrue(card(design, OLD_NODE)!!.entry!!.isSelected)
        }
        capture(scenario, design, "queue-fixture-projected")
        scenario.onActivity {
            design.rejectProxySelection(requests[3].selectionToken)
            assertSnapped(card(design, OLD_NODE)!!, selected = true)
            assertSnapped(card(design, NEW_NODE)!!, selected = false)
            design.rejectProxySelection(requests[1].selectionToken)
            assertSnapped(card(design, NEW_NODE)!!, selected = true)
            assertSnapped(card(design, OLD_NODE)!!, selected = false)
        }
        awaitCard(scenario, design, NEW_NODE, selected = true)
        capture(scenario, design, "queue-fixture-confirmed")
        scenario.onActivity { design.binding.viewPager.setCurrentItem(1, false) }
        awaitCard(scenario, design, MANUAL, selected = true, subtitle = "Selector($NEW_NODE)")
        capture(scenario, design, "queue-fixture-parent-confirmed")
    }

    @Test
    fun rejectedProjectionResetsTransitionOnDetachedPage() = withFixture(fixtureWithOffscreenPages()) { scenario, design ->
        awaitCard(scenario, design, OLD_NODE, selected = true)
        lateinit var rejectedRequest: ProxyDesign.Request.SelectProxy
        scenario.onActivity {
            assertTrue(card(design, NEW_NODE)!!.performClick())
            rejectedRequest = assertProjectedSelectionRequest(
                design.requests.tryReceive().getOrNull(),
                NEW_NODE,
            )
            val transitioningCard = checkNotNull(card(design, NEW_NODE))
            val transition = ProxyCardView::class.java.getDeclaredField("selectionColorTransition")
                .apply { isAccessible = true }.get(transitioningCard) as ProxySelectionColorTransition
            val target = ProxyCardView::class.java.getDeclaredMethod(
                "backgroundColor",
                rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry::class.java,
            ).apply { isAccessible = true }.invoke(transitioningCard, transitioningCard.entry) as Int
            assertTrue("The fixture must leave an in-flight projected color", transition.advance(target, System.currentTimeMillis() + 100).animating)
            design.binding.viewPager.setCurrentItem(3, false)
        }
        awaitCondition(scenario) {
            val pager = design.binding.viewPager.getChildAt(0) as RecyclerView
            design.binding.viewPager.currentItem == 3 && pager.findViewHolderForAdapterPosition(0) == null
        }
        scenario.onActivity { design.rejectProxySelection(rejectedRequest.selectionToken) }
        scenario.onActivity { design.binding.viewPager.setCurrentItem(0, false) }
        awaitCard(scenario, design, OLD_NODE, selected = true)
        scenario.onActivity {
            assertSnapped(card(design, OLD_NODE)!!, selected = true)
            assertSnapped(card(design, NEW_NODE)!!, selected = false)
        }
    }

    @Test
    fun realQueuedFailuresPreserveTheConfirmedNodeAndRecreatedActivity() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realCoreQueue") == "true")
        val backend = BackendProvider.provide()
        var before = emptyList<ProxyGroupSnapshot>()
        var originalGroup: String? = null
        var originalChoice: String? = null
        val outcomes = CopyOnWriteArrayList<Pair<String, String?>>()
        // Observe the real backend, without substituting results or delaying calls.
        val recordingBackend = object : ChimeraBackend by backend {
            override suspend fun selectProxy(groupName: String, proxyName: String) {
                try {
                    backend.selectProxy(groupName, proxyName)
                    outcomes += proxyName to null
                } catch (error: Exception) {
                    outcomes += proxyName to error.message.orEmpty()
                    throw error
                }
            }
        }
        ActivityScenario.launch(MetaMainActivity::class.java).use { main ->
            main.onActivity { assertEquals(ServiceState.STOPPED, backend.serviceState.value) }
            try {
                runBlocking(Dispatchers.IO) {
                    backend.awaitReady()
                    check(backend.prepareStartVpn() == StartVpnResult.PermissionNotRequired)
                    backend.startVpnAfterPermission()
                }
                awaitCondition(main) { backend.serviceState.value == ServiceState.RUNNING }
                before = runBlocking(Dispatchers.IO) { backend.listProxyGroups() }
                val pages = ProxyDisplayPolicy.groupPages(
                    before,
                    unavailableType = "Unavailable",
                    excludeNotSelectable = preferences.getBoolean("proxy_exclude_not_selectable", false),
                )
                val eligible = before.filter { selector ->
                    selector.proxyDetails[selector.name]?.type == "Selector" &&
                        selector.selected != null &&
                        selector.proxies.any { it != selector.selected && selector.proxyDetails[it]?.isGroup == false } &&
                        pages.any { page -> page.name != selector.name && page.proxies.any { it.name == selector.name } }
                }
                val group = checkNotNull(eligible.firstOrNull { it.name == originalLastGroup } ?: eligible.firstOrNull())
                val selected = checkNotNull(group.selected)
                originalGroup = group.name
                originalChoice = selected
                check(before.none { MISSING_NODE in it.proxyDetails || MISSING_NODE in it.proxies })
                preferences.edit { putString("proxy_last_group", group.name) }
                ActivityScenario.launch(MetaProxyDesignActivity::class.java).use { scenario ->
                    lateinit var design: ProxyDesign
                    fun readDesign() = scenario.onActivity { design = activityDesign(it) }
                    readDesign()
                    awaitCard(scenario, design, selected, selected = true)
                    val candidates = group.proxies.filter { it != selected && group.proxyDetails[it]?.isGroup == false }
                    var alternative: String? = null
                    awaitCondition(scenario) {
                        alternative = candidates.firstOrNull { card(design, it)?.isShown == true }
                        alternative != null
                    }
                    val target = checkNotNull(alternative)
                    capture(scenario, design, "queue-real-before")
                    scenario.onActivity {
                        design.setGroups(before.map { snapshot ->
                            if (snapshot.name != group.name) snapshot else snapshot.copy(
                                proxies = listOf(MISSING_NODE) + snapshot.proxies,
                                proxyDetails = snapshot.proxyDetails +
                                    (MISSING_NODE to ProxySnapshot(MISSING_NODE, "Vless", emptyList())),
                            )
                        })
                    }
                    awaitCard(scenario, design, MISSING_NODE, selected = false)
                    awaitCard(scenario, design, target, selected = false)
                    scenario.onActivity { activity ->
                        MetaProxyDesignActivity::class.java.getDeclaredField("backend")
                            .apply { isAccessible = true }.set(activity, recordingBackend)
                        listOf(target, MISSING_NODE, target, MISSING_NODE).forEach { name ->
                            assertTrue(card(design, name)!!.performClick())
                            assertTrue("Each click immediately projects its node", card(design, name)!!.entry!!.isSelected)
                        }
                        assertEquals("All four clicks must be queued before completion", 4, pendingSelections(activity))
                    }
                    awaitCondition(scenario) {
                        val error = scenarioSnackbar(design.root)?.text
                        error?.startsWith(design.context.getString(R.string.proxy_select_failed, "")) == true &&
                            error.contains("400 Bad Request") &&
                            card(design, target)?.entry?.isSelected == true &&
                            card(design, MISSING_NODE) == null
                    }
                    scenario.onActivity { activity ->
                        assertEquals(0, pendingSelections(activity))
                        assertSnapped(card(design, target)!!, selected = true)
                    }
                    val statuses = outcomes.map { (_, error) ->
                        when {
                            error == null -> "success"
                            error.contains("400 Bad Request") -> "HTTP400"
                            else -> "other-error"
                        }
                    }
                    File(instrumentation.targetContext.cacheDir, "proxy-queue-outcomes.json")
                        .writeText(statuses.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]"))
                    assertEquals(listOf(target, MISSING_NODE, target, MISSING_NODE), outcomes.map { it.first })
                    assertEquals(listOf(true, false, true, false), outcomes.map { it.second == null })
                    outcomes.filter { it.first == MISSING_NODE }.forEach {
                        assertTrue("Each stale request must receive a real core HTTP400", it.second.orEmpty().contains("400 Bad Request"))
                    }
                    val after = runBlocking(Dispatchers.IO) { backend.listProxyGroups() }
                    assertEquals(selectorChoices(before) + (group.name to target), selectorChoices(after))
                    capture(scenario, design, "queue-real-confirmed")
                    scenario.recreate()
                    readDesign()
                    awaitCard(scenario, design, target, selected = true)
                    scenario.onActivity { assertEquals(0, pendingSelections(it)) }
                    capture(scenario, design, "queue-real-recreated")
                    val parentIndex = pages.indexOfFirst { page ->
                        page.name != group.name && page.proxies.any { it.name == group.name }
                    }
                    val parentEntry = pages[parentIndex].proxies.first { it.name == group.name }
                    scenario.onActivity { design.binding.viewPager.setCurrentItem(parentIndex, false) }
                    awaitCard(scenario, design, group.name, selected = parentEntry.isSelected, subtitle = "Selector($target)")
                    capture(scenario, design, "queue-real-parent-confirmed")
                    println("Four real queued clicks, repeated core rejection, confirmed selection and post-drain recreation verified.")
                }
            } finally {
                try {
                    if (originalGroup != null && originalChoice != null) {
                        assertEquals("Core must remain available to restore selectors", ServiceState.RUNNING, backend.serviceState.value)
                        runBlocking(Dispatchers.IO) {
                            backend.selectProxy(checkNotNull(originalGroup), checkNotNull(originalChoice))
                            assertEquals("Restore every selector choice", selectorChoices(before), selectorChoices(backend.listProxyGroups()))
                        }
                    }
                } finally {
                    runBlocking(Dispatchers.IO) { backend.stopVpn() }
                    val deadline = SystemClock.uptimeMillis() + 15_000
                    while (backend.serviceState.value != ServiceState.STOPPED && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(20)
                    }
                    assertEquals(ServiceState.STOPPED, backend.serviceState.value)
                }
            }
        }
    }

    @Test
    fun realCoreRejectsAStaleNodeAndTheActualActivityRestoresItsSnapshot() {
        // Opt-in only: requires an isolated device, an existing profile and VPN permission.
        // The ordinary CI suite must not start a VPN or require a private subscription.
        assumeTrue(InstrumentationRegistry.getArguments().getString("realCoreRollback") == "true")
        val backend = BackendProvider.provide()
        ActivityScenario.launch(MetaMainActivity::class.java).use { main ->
            main.onActivity { assertEquals(ServiceState.STOPPED, backend.serviceState.value) }
            try {
                runBlocking(Dispatchers.IO) {
                    backend.awaitReady()
                    check(backend.prepareStartVpn() == StartVpnResult.PermissionNotRequired)
                    backend.startVpnAfterPermission()
                }
                awaitCondition(main) { backend.serviceState.value == ServiceState.RUNNING }
                val before = runBlocking(Dispatchers.IO) { backend.listProxyGroups() }
                val visiblePages = ProxyDisplayPolicy.groupPages(
                    before,
                    unavailableType = "Unavailable",
                    excludeNotSelectable = preferences.getBoolean("proxy_exclude_not_selectable", false),
                )
                val eligibleGroups = before.filter { selector ->
                    selector.proxyDetails[selector.name]?.type == "Selector" &&
                        selector.selected != null &&
                        visiblePages.any { page -> page.name != selector.name && page.proxies.any { it.name == selector.name } }
                }
                val group = eligibleGroups.firstOrNull { it.name == originalLastGroup }
                    ?: checkNotNull(eligibleGroups.firstOrNull()) { "The opt-in profile must include a linked selector group" }
                val selected = checkNotNull(group.selected)
                check(before.none { MISSING_NODE in it.proxyDetails || MISSING_NODE in it.proxies })
                preferences.edit { putString("proxy_last_group", group.name) }
                ActivityScenario.launch(MetaProxyDesignActivity::class.java).use { scenario ->
                    lateinit var design: ProxyDesign
                    scenario.onActivity { activity ->
                        design = MetaProxyDesignActivity::class.java.getDeclaredField("design")
                            .apply { isAccessible = true }.get(activity) as ProxyDesign
                    }
                    awaitCard(scenario, design, selected, selected = true)
                    capture(scenario, design, "real-before")
                    scenario.onActivity {
                        // Simulate a stale rendered provider result, never a stored profile edit.
                        design.setGroups(before.map { snapshot ->
                            if (snapshot.name != group.name) snapshot else snapshot.copy(
                                proxies = listOf(MISSING_NODE) + snapshot.proxies,
                                proxyDetails = snapshot.proxyDetails +
                                    (MISSING_NODE to ProxySnapshot(MISSING_NODE, "Vless", emptyList())),
                            )
                        })
                    }
                    awaitCard(scenario, design, MISSING_NODE, selected = false)
                    scenario.onActivity {
                        assertTrue(card(design, MISSING_NODE)!!.performClick())
                        assertTrue("Projection is visible before the IO result", card(design, MISSING_NODE)!!.entry!!.isSelected)
                    }
                    awaitCondition(scenario) {
                        val error = scenarioSnackbar(design.root)
                        error?.text?.startsWith(design.context.getString(R.string.proxy_select_failed, "")) == true &&
                            card(design, selected)?.entry?.isSelected == true &&
                            card(design, MISSING_NODE) == null
                    }
                    scenario.onActivity { assertSnapped(card(design, selected)!!, selected = true) }
                    capture(scenario, design, "real-rejected-restored")
                    val after = runBlocking(Dispatchers.IO) { backend.listProxyGroups() }
                    assertEquals(selectorChoices(before), selectorChoices(after))
                    val pages = ProxyDisplayPolicy.groupPages(
                        after,
                        unavailableType = "Unavailable",
                        excludeNotSelectable = preferences.getBoolean("proxy_exclude_not_selectable", false),
                    )
                    val parentIndex = pages.indexOfFirst { page ->
                        page.name != group.name && page.proxies.any { it.name == group.name }
                    }
                    assertTrue("The opt-in profile must include a linked parent group", parentIndex >= 0)
                    scenario.onActivity { design.binding.viewPager.setCurrentItem(parentIndex, false) }
                    awaitCondition(scenario) { card(design, group.name)?.entry?.subtitle == "Selector($selected)" }
                    capture(scenario, design, "real-parent-restored")
                    println("Real core rejected the transient stale node; all selector choices remain unchanged.")
                }
            } finally {
                runBlocking(Dispatchers.IO) { backend.stopVpn() }
                val deadline = SystemClock.uptimeMillis() + 15_000
                while (backend.serviceState.value != ServiceState.STOPPED && SystemClock.uptimeMillis() < deadline) {
                    SystemClock.sleep(20)
                }
                assertEquals(ServiceState.STOPPED, backend.serviceState.value)
            }
        }
    }

    @Test
    fun recreationCancelsAnUndispatchedSelectionAndReloadsTheOriginalNode() =
        verifyPendingRecreation(RecreationBoundary.BEFORE_DISPATCH)

    @Test
    fun recreationReadsACommittedCoreSelectionBeforeTheOldUiReceivesItsResult() =
        verifyPendingRecreation(RecreationBoundary.AFTER_COMMIT)

    @Test
    fun recreationDiscardsARejectedProjectionBeforeTheOldUiReceivesItsError() =
        verifyPendingRecreation(RecreationBoundary.AFTER_REJECTION)

    private enum class RecreationBoundary { BEFORE_DISPATCH, AFTER_COMMIT, AFTER_REJECTION }

    private fun verifyPendingRecreation(boundary: RecreationBoundary) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realCoreRecreation") == "true")
        val backend = BackendProvider.provide()
        val reachedBoundary = CompletableDeferred<Unit>()
        val cancellationObserved = CompletableDeferred<Unit>()
        val actualCalls = AtomicInteger()
        val coreOutcome = AtomicReference("not-dispatched")
        val holdingBackend = object : ChimeraBackend by backend {
            override suspend fun selectProxy(groupName: String, proxyName: String) {
                if (boundary != RecreationBoundary.BEFORE_DISPATCH) {
                    actualCalls.incrementAndGet()
                    try {
                        backend.selectProxy(groupName, proxyName)
                        coreOutcome.set("success")
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        check(boundary == RecreationBoundary.AFTER_REJECTION && error.message.orEmpty().contains("400 Bad Request"))
                        coreOutcome.set("HTTP400")
                    }
                }
                reachedBoundary.complete(Unit)
                // Deterministic boundary only; no invented core result or socket interruption.
                try {
                    awaitCancellation()
                } catch (cancellation: CancellationException) {
                    cancellationObserved.complete(Unit)
                    throw cancellation
                }
            }
        }
        var before = emptyList<ProxyGroupSnapshot>()
        var originalGroup: String? = null
        var originalChoice: String? = null
        val capturePrefix = "recreation-${boundary.name.lowercase()}"
        ActivityScenario.launch(MetaMainActivity::class.java).use { main ->
            main.onActivity { assertEquals(ServiceState.STOPPED, backend.serviceState.value) }
            try {
                runBlocking(Dispatchers.IO) {
                    backend.awaitReady()
                    check(backend.prepareStartVpn() == StartVpnResult.PermissionNotRequired)
                    backend.startVpnAfterPermission()
                }
                awaitCondition(main) { backend.serviceState.value == ServiceState.RUNNING }
                before = runBlocking(Dispatchers.IO) { backend.listProxyGroups() }
                val pages = ProxyDisplayPolicy.groupPages(
                    before,
                    unavailableType = "Unavailable",
                    excludeNotSelectable = preferences.getBoolean("proxy_exclude_not_selectable", false),
                )
                val eligible = before.filter { selector ->
                    selector.proxyDetails[selector.name]?.type == "Selector" &&
                        selector.selected != null &&
                        selector.proxies.any { it != selector.selected && selector.proxyDetails[it]?.isGroup == false } &&
                        pages.any { page -> page.name != selector.name && page.proxies.any { it.name == selector.name } }
                }
                val group = checkNotNull(eligible.firstOrNull { it.name == originalLastGroup } ?: eligible.firstOrNull())
                val selected = checkNotNull(group.selected)
                originalGroup = group.name
                originalChoice = selected
                check(before.none { MISSING_NODE in it.proxyDetails || MISSING_NODE in it.proxies })
                preferences.edit { putString("proxy_last_group", group.name) }
                ActivityScenario.launch(MetaProxyDesignActivity::class.java).use { scenario ->
                    lateinit var oldActivity: MetaProxyDesignActivity
                    lateinit var design: ProxyDesign
                    scenario.onActivity {
                        oldActivity = it
                        design = activityDesign(it)
                    }
                    awaitCard(scenario, design, selected, selected = true)
                    var alternative: String? = null
                    awaitCondition(scenario) {
                        alternative = group.proxies.firstOrNull { name ->
                            name != selected && group.proxyDetails[name]?.isGroup == false && card(design, name)?.isShown == true
                        }
                        alternative != null
                    }
                    val requested = if (boundary == RecreationBoundary.AFTER_REJECTION) MISSING_NODE else checkNotNull(alternative)
                    val confirmed = if (boundary == RecreationBoundary.AFTER_COMMIT) requested else selected
                    capture(scenario, design, "$capturePrefix-before")
                    if (boundary == RecreationBoundary.AFTER_REJECTION) {
                        scenario.onActivity {
                            design.setGroups(before.map { snapshot ->
                                if (snapshot.name != group.name) snapshot else snapshot.copy(
                                    proxies = listOf(MISSING_NODE) + snapshot.proxies,
                                    proxyDetails = snapshot.proxyDetails +
                                        (MISSING_NODE to ProxySnapshot(MISSING_NODE, "Vless", emptyList())),
                                )
                            })
                        }
                        awaitCard(scenario, design, MISSING_NODE, selected = false)
                    }
                    scenario.onActivity { activity ->
                        MetaProxyDesignActivity::class.java.getDeclaredField("backend")
                            .apply { isAccessible = true }.set(activity, holdingBackend)
                        assertTrue(card(design, requested)!!.performClick())
                        assertTrue(card(design, requested)!!.entry!!.isSelected)
                    }
                    runBlocking { withTimeout(15_000) { reachedBoundary.await() } }
                    scenario.onActivity { assertEquals(1, pendingSelections(it)) }
                    val expected = selectorChoices(before) + (group.name to confirmed)
                    assertEquals(expected, selectorChoices(runBlocking(Dispatchers.IO) { backend.listProxyGroups() }))
                    capture(scenario, design, "$capturePrefix-pending")
                    scenario.recreate()
                    runBlocking { withTimeout(15_000) { cancellationObserved.await() } }
                    scenario.onActivity { activity ->
                        assertNotSame(oldActivity, activity)
                        assertTrue(oldActivity.isDestroyed)
                        design = activityDesign(activity)
                        assertSame(
                            backend,
                            MetaProxyDesignActivity::class.java.getDeclaredField("backend").apply { isAccessible = true }.get(activity),
                        )
                    }
                    awaitCard(scenario, design, confirmed, selected = true)
                    awaitCondition(scenario) { pendingSelections(oldActivity) == 0 }
                    scenario.onActivity { activity ->
                        assertEquals(0, pendingSelections(activity))
                        assertSnapped(card(design, confirmed)!!, selected = true)
                        assertNull("Cancellation must not show a selection error", scenarioSnackbar(design.root))
                        assertNull(card(design, MISSING_NODE))
                    }
                    assertEquals(expected, selectorChoices(runBlocking(Dispatchers.IO) { backend.listProxyGroups() }))
                    assertEquals(if (boundary == RecreationBoundary.BEFORE_DISPATCH) 0 else 1, actualCalls.get())
                    val expectedOutcome = when (boundary) {
                        RecreationBoundary.BEFORE_DISPATCH -> "not-dispatched"
                        RecreationBoundary.AFTER_COMMIT -> "success"
                        RecreationBoundary.AFTER_REJECTION -> "HTTP400"
                    }
                    assertEquals(expectedOutcome, coreOutcome.get())
                    capture(scenario, design, "$capturePrefix-restored")
                    val parentIndex = pages.indexOfFirst { page ->
                        page.name != group.name && page.proxies.any { it.name == group.name }
                    }
                    val parentEntry = pages[parentIndex].proxies.first { it.name == group.name }
                    scenario.onActivity { design.binding.viewPager.setCurrentItem(parentIndex, false) }
                    awaitCard(scenario, design, group.name, selected = parentEntry.isSelected, subtitle = "Selector($confirmed)")
                    capture(scenario, design, "$capturePrefix-parent-restored")
                    File(instrumentation.targetContext.cacheDir, "$capturePrefix.json").writeText(
                        """{"boundary":"${boundary.name}","coreResult":"${coreOutcome.get()}","coreCalls":${actualCalls.get()},"cancelled":true,"newActivity":true,"pending":0}""",
                    )
                }
            } finally {
                try {
                    if (originalGroup != null && originalChoice != null) {
                        assertEquals(ServiceState.RUNNING, backend.serviceState.value)
                        runBlocking(Dispatchers.IO) {
                            backend.selectProxy(checkNotNull(originalGroup), checkNotNull(originalChoice))
                            assertEquals(selectorChoices(before), selectorChoices(backend.listProxyGroups()))
                        }
                    }
                } finally {
                    runBlocking(Dispatchers.IO) { backend.stopVpn() }
                    val deadline = SystemClock.uptimeMillis() + 15_000
                    while (backend.serviceState.value != ServiceState.STOPPED && SystemClock.uptimeMillis() < deadline) {
                        SystemClock.sleep(20)
                    }
                    assertEquals(ServiceState.STOPPED, backend.serviceState.value)
                }
            }
        }
    }

    private fun withFixture(
        groups: List<ProxyGroupSnapshot> = fixture(),
        block: (ActivityScenario<MetaMainActivity>, ProxyDesign) -> Unit,
    ) {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: ProxyDesign
            scenario.onActivity { activity ->
                preferences.edit {
                    putString("proxy_last_group", MANUAL)
                    putInt("proxy_columns", 2)
                }
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                design = ProxyDesign(activity)
                activity.setContentView(design.root)
                design.setGroups(groups)
            }
            awaitCard(scenario, design, OLD_NODE, selected = true)
            scenario.onActivity {
                val bars = checkNotNull(ViewCompat.getRootWindowInsets(design.root))
                    .getInsets(WindowInsetsCompat.Type.systemBars())
                assertEquals(bars.top, design.root.findViewById<View>(R.id.proxy_activity_bar).paddingTop)
            }
            block(scenario, design)
        }
    }

    private fun <A : androidx.activity.ComponentActivity> awaitCard(
        scenario: ActivityScenario<A>,
        design: ProxyDesign,
        name: String,
        selected: Boolean,
        subtitle: String? = null,
    ) = awaitCondition(scenario) {
        card(design, name)?.entry?.let { it.isSelected == selected && (subtitle == null || it.subtitle == subtitle) } == true
    }

    private fun card(design: ProxyDesign, name: String): ProxyCardView? {
        val pager = design.binding.viewPager.getChildAt(0) as RecyclerView
        val page = pager.findViewHolderForAdapterPosition(design.binding.viewPager.currentItem)?.itemView ?: return null
        fun find(view: View): ProxyCardView? {
            if (view is ProxyCardView && view.entry?.name == name) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        return find(page)
    }

    private fun assertSnapped(card: ProxyCardView, selected: Boolean) {
        assertEquals(selected, card.entry!!.isSelected)
        val transition = ProxyCardView::class.java.getDeclaredField("selectionColorTransition")
            .apply { isAccessible = true }.get(card) as ProxySelectionColorTransition
        val target = ProxyCardView::class.java.getDeclaredMethod(
            "backgroundColor", rs.chimera.android.ui.metacubex.design.ProxyDisplayEntry::class.java,
        ).apply { isAccessible = true }.invoke(card, card.entry) as Int
        val frame = transition.advance(target, SystemClock.uptimeMillis())
        assertEquals(target, frame.color)
        assertFalse("Rollback must snap, not animate a rejected choice", frame.animating)
    }

    private fun scenarioSnackbar(root: View): TextView? =
        root.rootView.findViewById(com.google.android.material.R.id.snackbar_text)

    private fun activityDesign(activity: MetaProxyDesignActivity): ProxyDesign =
        MetaProxyDesignActivity::class.java.getDeclaredField("design")
            .apply { isAccessible = true }.get(activity) as ProxyDesign

    private fun pendingSelections(activity: MetaProxyDesignActivity): Int =
        MetaProxyDesignActivity::class.java.getDeclaredField("pendingProxySelections")
            .apply { isAccessible = true }.getInt(activity)

    private fun <A : androidx.activity.ComponentActivity> awaitCondition(scenario: ActivityScenario<A>, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        var matched = false
        while (!matched && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { matched = condition() }
            if (!matched) SystemClock.sleep(20)
        }
        assertTrue("The proxy page must reach the expected confirmed state", matched)
    }

    private fun <A : androidx.activity.ComponentActivity> capture(scenario: ActivityScenario<A>, design: ProxyDesign, state: String) {
        if (InstrumentationRegistry.getArguments().getString("captureRollback") != "true") return
        awaitCondition(scenario) {
            val fab = design.binding.fabDelayTest
            !design.root.isLayoutRequested && fab.isShown && fab.alpha == 1f && fab.scaleX == 1f && fab.scaleY == 1f
        }
        val committed = CountDownLatch(1)
        scenario.onActivity {
            val bars = checkNotNull(ViewCompat.getRootWindowInsets(design.root)).getInsets(WindowInsetsCompat.Type.systemBars())
            assertEquals(bars.top, design.root.findViewById<View>(R.id.proxy_activity_bar).paddingTop)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && design.root.isHardwareAccelerated) {
                design.root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                design.root.invalidate()
            } else {
                design.root.postOnAnimation { design.root.postOnAnimation { committed.countDown() } }
            }
        }
        assertTrue(committed.await(5, TimeUnit.SECONDS))
        val dark = instrumentation.targetContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val file = File(instrumentation.targetContext.cacheDir, "proxy-rollback-${if (dark) "dark" else "light"}-$state.png")
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val resolver = instrumentation.targetContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/AChimera-CMFA-Comparisons/ProxyRollback",
                    )
                }
            }
            val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
            resolver.openOutputStream(uri, "w").use { output ->
                checkNotNull(output).use { stream ->
                    file.inputStream().use { input -> input.copyTo(stream) }
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun selectorChoices(groups: List<ProxyGroupSnapshot>) = groups
        .filter { it.proxyDetails[it.name]?.type == "Selector" }.associate { it.name to it.selected }

    private fun requireSelectionRequest(request: ProxyDesign.Request?): ProxyDesign.Request.SelectProxy {
        val selection = request as? ProxyDesign.Request.SelectProxy
            ?: throw AssertionError("Expected a proxy selection request, got $request")
        assertTrue(selection.selectionProjected)
        return selection
    }

    private fun assertProjectedSelectionRequest(
        request: ProxyDesign.Request?,
        proxyName: String,
    ): ProxyDesign.Request.SelectProxy {
        val selection = requireSelectionRequest(request)
        assertEquals(MANUAL, selection.groupName)
        assertEquals(proxyName, selection.proxyName)
        return selection
    }

    private fun fixture(): List<ProxyGroupSnapshot> {
        val details = mapOf(
            MANUAL to ProxySnapshot(MANUAL, "Selector", emptyList(), true, OLD_NODE),
            PARENT to ProxySnapshot(PARENT, "Selector", emptyList(), true, MANUAL),
            OLD_NODE to ProxySnapshot(OLD_NODE, "Vless", emptyList()),
            NEW_NODE to ProxySnapshot(NEW_NODE, "Vless", emptyList()),
        )
        return listOf(
            ProxyGroupSnapshot(MANUAL, listOf(OLD_NODE, NEW_NODE), OLD_NODE, ProxyMode.RULE, details),
            ProxyGroupSnapshot(PARENT, listOf(MANUAL), MANUAL, ProxyMode.RULE, details),
        )
    }

    private fun fixtureWithOffscreenPages(): List<ProxyGroupSnapshot> {
        val base = fixture()
        val extraGroups = (2..3).map { index ->
            val name = "Offscreen $index"
            val details = base.first().proxyDetails + (name to ProxySnapshot(name, "Selector", emptyList(), true, OLD_NODE))
            ProxyGroupSnapshot(name, listOf(OLD_NODE, NEW_NODE), OLD_NODE, ProxyMode.RULE, details)
        }
        return base + extraGroups
    }

    private companion object {
        const val MANUAL = "Manual"
        const val PARENT = "Parent"
        const val OLD_NODE = "node-a"
        const val NEW_NODE = "node-b"
        const val MISSING_NODE = "UI parity removed node"
    }
}
