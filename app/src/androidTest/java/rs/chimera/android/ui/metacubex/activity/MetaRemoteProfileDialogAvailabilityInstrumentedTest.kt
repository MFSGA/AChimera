package rs.chimera.android.ui.metacubex.activity

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.hamcrest.Matchers.not
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R

@RunWith(AndroidJUnit4::class)
class MetaRemoteProfileDialogAvailabilityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun runningVpnDisablesEditingButRetainsDraftAndAllowsCancel() {
        lateinit var controller: MetaRemoteProfileDialogController
        val profileName = "Draft Profile"
        val device = UiDevice.getInstance(instrumentation)

        device.pressHome()
        device.waitForIdle()

        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            awaitActivityWindowFocus(scenario)
            scenario.onActivity { activity ->
                controller = showMetaRemoteProfileDialog(
                    context = activity,
                    titleRes = R.string.profile_edit_settings,
                    positiveButtonRes = R.string.save,
                    initialName = profileName,
                    initialUrl = "https://example.invalid/profile.yaml",
                    requireName = true,
                ) { _, _, _ -> }
            }
            instrumentation.waitForIdleSync()

            onView(withHint(R.string.profile_name_label)).check(matches(isEnabled()))

            scenario.onActivity { controller.setEditingEnabled(false) }
            instrumentation.waitForIdleSync()

            onView(withHint(R.string.profile_name_label)).check(matches(not(isEnabled())))
            onView(withHint(R.string.profile_name_label)).check(matches(withText(profileName)))
            onView(withId(android.R.id.button1)).check(matches(not(isEnabled())))
            onView(withId(android.R.id.button2)).check(matches(isEnabled()))

            scenario.onActivity { controller.setEditingEnabled(true) }
            instrumentation.waitForIdleSync()

            onView(withHint(R.string.profile_name_label)).check(matches(isEnabled()))
            onView(withHint(R.string.profile_name_label)).check(matches(withText(profileName)))
        }
    }

    private fun awaitActivityWindowFocus(scenario: ActivityScenario<MetaMainActivity>) {
        val deadline = SystemClock.uptimeMillis() + WINDOW_FOCUS_TIMEOUT_MS
        var hasWindowFocus = false
        while (!hasWindowFocus && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                hasWindowFocus = activity.hasWindowFocus()
            }
            if (!hasWindowFocus) SystemClock.sleep(WINDOW_FOCUS_POLL_INTERVAL_MS)
        }
        check(hasWindowFocus) { "MetaMainActivity window did not gain focus after launch" }
    }

    private companion object {
        const val WINDOW_FOCUS_TIMEOUT_MS = 5_000L
        const val WINDOW_FOCUS_POLL_INTERVAL_MS = 50L
    }
}
