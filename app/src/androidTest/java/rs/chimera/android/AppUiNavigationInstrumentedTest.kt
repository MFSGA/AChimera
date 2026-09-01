package rs.chimera.android

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.LayoutInflater
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.ui.metacubex.activity.MetaConnectionsActivity
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.metacubex.activity.MetaSettingsActivity
import rs.chimera.android.ui.navigation.DefaultAppUiRouter
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.UiVariant

@RunWith(AndroidJUnit4::class)
class AppUiNavigationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context
        get() = instrumentation.targetContext

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        clearPreferences()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
        clearPreferences()
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
    fun metaConnectionsLayoutStartsWithCloseAllDisabled() {
        val root = LayoutInflater.from(context).inflate(R.layout.meta_design_connections, null)

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
    fun metaSettingsEntryOpensSettingsScreen() {
        ActivityScenario.launch(MetaMainActivity::class.java).use {
            waitForResumedActivity<MetaMainActivity>()
            onView(withId(R.id.card_settings)).perform(click())
            waitForResumedActivity<MetaSettingsActivity>()
            onView(withId(R.id.toolbar)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun watfaqSettingsEntryOpensSettingsScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitForResumedActivity<MainActivity>()
            composeRule.onNodeWithText(context.getString(R.string.settings_screen)).performClick()
            composeRule.onNodeWithText(context.getString(R.string.settings_title)).assertIsDisplayed()
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
