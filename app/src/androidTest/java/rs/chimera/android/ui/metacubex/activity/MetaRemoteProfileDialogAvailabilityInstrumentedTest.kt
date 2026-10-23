package rs.chimera.android.ui.metacubex.activity

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
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
}
