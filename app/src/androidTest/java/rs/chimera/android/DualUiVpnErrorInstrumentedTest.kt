package rs.chimera.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Context
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.backend.BackendRuntimeState
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.VpnRuntimeStatus
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.UiVariant

@RunWith(AndroidJUnit4::class)
class DualUiVpnErrorInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private var hadStoredUiVariant = false
    private var originalStoredUiVariant: String? = null
    private var originallyHadNotificationPermission = false
    private var originalRuntimeStatus: VpnRuntimeStatus? = null
    private val context
        get() = instrumentation.targetContext

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            originallyHadNotificationPermission =
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val settingsPreferences = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        hadStoredUiVariant = settingsPreferences.contains(UI_VARIANT_KEY)
        originalStoredUiVariant = settingsPreferences.getString(UI_VARIANT_KEY, null)
        originalRuntimeStatus = BackendRuntimeState.status.value
        AppPreferences.updateUiVariant(context, UiVariant.WATFAQ)
        BackendRuntimeState.updateServiceError(SERVICE_ERROR)
    }

    @After
    fun tearDown() {
        BackendRuntimeState.updateServiceState(ServiceState.STOPPED)
        originalRuntimeStatus?.let { status ->
            if (status.error != null) {
                BackendRuntimeState.updateServiceError(status.error)
            } else {
                BackendRuntimeState.updateServiceState(status.state)
            }
        }
        val settingsPreferences = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val restoreUiVariant = settingsPreferences.edit().let { editor ->
            if (hadStoredUiVariant) {
                editor.putString(UI_VARIANT_KEY, originalStoredUiVariant)
            } else {
                editor.remove(UI_VARIANT_KEY)
            }
        }.commit()
        check(restoreUiVariant) { "Failed to restore the original UI variant preference" }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !originallyHadNotificationPermission
        ) {
            instrumentation.uiAutomation.revokeRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }

    @Test
    fun bothUiRootsDisplayTheSameServiceError() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNode(hasScrollAction())
                .performScrollToNode(hasText(SERVICE_ERROR))
            composeRule.onNodeWithText(SERVICE_ERROR).assertIsDisplayed()
        }

        ActivityScenario.launch(MetaMainActivity::class.java).use {
            onView(withId(R.id.text_status_subtitle))
                .check(matches(withText(containsString(SERVICE_ERROR))))
        }
    }

    private companion object {
        const val SERVICE_ERROR = "core shutdown timed out"
        const val SETTINGS_PREFS = "settings"
        const val UI_VARIANT_KEY = "ui_variant"
    }
}
