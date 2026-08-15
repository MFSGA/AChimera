package rs.chimera.android

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.ui.metacubex.activity.MetaSettingsActivity
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.UiVariant

@RunWith(AndroidJUnit4::class)
class DualUiSettingsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context
        get() = instrumentation.targetContext
    private val prefs
        get() = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    private val backend = BackendProvider.provide()

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        AppPreferences.updateUiVariant(context, UiVariant.WATFAQ)
        resetRuntimeSettings()
    }

    @After
    fun tearDown() {
        resetRuntimeSettings()
        AppPreferences.updateUiVariant(context, UiVariant.WATFAQ)
    }

    @Test
    fun backendSettingsAreRenderedByBothUiRoots() {
        updateRuntimeSettings(
            allowLan = true,
            fakeIp = true,
            ipv6 = true,
            mixedPort = TEST_MIXED_PORT,
            httpPort = TEST_HTTP_PORT,
            socksPort = TEST_SOCKS_PORT,
        )
        val portsSummary = context.getString(
            R.string.cmfa_ports_summary,
            TEST_MIXED_PORT.toInt(),
            TEST_HTTP_PORT.toString(),
            TEST_SOCKS_PORT.toString(),
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            openWatfaqSettings()
            scrollWatfaqTo(context.getString(R.string.settings_allow_lan)).assertIsOn()
            scrollWatfaqTo(context.getString(R.string.settings_fake_ip)).assertIsOn()
            scrollWatfaqTo(context.getString(R.string.settings_ipv6)).assertIsOn()
            scrollWatfaqTo(portsSummary).assertIsDisplayed()
        }

        ActivityScenario.launch(MetaSettingsActivity::class.java).use {
            onView(withId(R.id.switch_allow_lan)).check(matches(isChecked()))
            onView(withId(R.id.switch_fake_ip)).check(matches(isChecked()))
            onView(withId(R.id.switch_ipv6)).check(matches(isChecked()))
            onView(withId(R.id.text_ports_summary)).check(matches(withText(portsSummary)))
        }
    }

    @Test
    fun diagnosticsEntriesAreReachableFromBothSettingsRoots() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openWatfaqSettings()
            scrollWatfaqTo(context.getString(R.string.dns_diagnostics_title)).assertIsDisplayed()
            scrollWatfaqTo(context.getString(R.string.rules_diagnostics_title)).assertIsDisplayed()
            scrollWatfaqTo(context.getString(R.string.proxy_providers_title)).assertIsDisplayed()
        }

        ActivityScenario.launch(MetaSettingsActivity::class.java).use {
            onView(withId(R.id.card_dns)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.card_rules)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.card_providers)).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }

    @Test
    fun watfaqSettingChangeIsObservedByMetaCubeX() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openWatfaqSettings()
            scrollWatfaqTo(context.getString(R.string.settings_allow_lan)).performClick()
            waitUntil("Watfaq LAN change was not persisted") {
                prefs.getBoolean(ALLOW_LAN_KEY, false)
            }
        }

        ActivityScenario.launch(MetaSettingsActivity::class.java).use {
            onView(withId(R.id.switch_allow_lan)).check(matches(isChecked()))
        }
    }

    @Test
    fun watfaqAppRoutingIsReachableFromSettings() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openWatfaqSettings()
            scrollWatfaqTo(context.getString(R.string.settings_app_filter)).performClick()
            composeRule.onNodeWithText(context.getString(R.string.app_selector_title))
                .assertIsDisplayed()
        }
    }

    private fun openWatfaqSettings() {
        composeRule.onNodeWithText(context.getString(R.string.settings_screen))
            .performClick()
        scrollWatfaqTo(context.getString(R.string.settings_allow_lan)).assertIsDisplayed()
    }

    private fun scrollWatfaqTo(text: String): SemanticsNodeInteraction {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
        return composeRule.onNodeWithText(text)
    }

    private fun resetRuntimeSettings() {
        runBlocking {
            backend.updateSettings(
                SettingsPatch(
                    allowLan = false,
                    fakeIp = false,
                    ipv6 = false,
                    mixedPort = DEFAULT_MIXED_PORT,
                    clearHttpPort = true,
                    clearSocksPort = true,
                    appFilterMode = "ALL",
                    allowedApps = emptySet(),
                    disallowedApps = emptySet(),
                ),
            )
        }
    }

    private fun updateRuntimeSettings(
        allowLan: Boolean,
        fakeIp: Boolean,
        ipv6: Boolean,
        mixedPort: UShort,
        httpPort: UShort?,
        socksPort: UShort?,
    ) {
        runBlocking {
            backend.updateSettings(
                SettingsPatch(
                    allowLan = allowLan,
                    fakeIp = fakeIp,
                    ipv6 = ipv6,
                    mixedPort = mixedPort,
                    httpPort = httpPort,
                    socksPort = socksPort,
                    clearHttpPort = httpPort == null,
                    clearSocksPort = socksPort == null,
                ),
            )
        }
    }

    private fun waitUntil(
        message: String,
        condition: () -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + FIND_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(POLL_INTERVAL_MS)
        }
        throw AssertionError(message)
    }

    private companion object {
        const val FIND_TIMEOUT_MS = 5_000L
        const val POLL_INTERVAL_MS = 50L
        const val SETTINGS_PREFS = "settings"
        const val ALLOW_LAN_KEY = "allow_lan"
        val DEFAULT_MIXED_PORT: UShort = 7890u
        val TEST_MIXED_PORT: UShort = 19_090u
        val TEST_HTTP_PORT: UShort = 18_080u
        val TEST_SOCKS_PORT: UShort = 11_080u
    }
}
