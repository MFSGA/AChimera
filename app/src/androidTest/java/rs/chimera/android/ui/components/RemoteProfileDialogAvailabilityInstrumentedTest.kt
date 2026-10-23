package rs.chimera.android.ui.components

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.MainActivity
import rs.chimera.android.R
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.UiVariant

@RunWith(AndroidJUnit4::class)
class RemoteProfileDialogAvailabilityInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var originalUiVariant = UiVariant.WATFAQ

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun setUp() {
        originalUiVariant = AppPreferences.uiVariant(context)
        AppPreferences.updateUiVariant(context, UiVariant.WATFAQ)
    }

    @After
    fun tearDown() {
        AppPreferences.updateUiVariant(context, originalUiVariant)
    }

    @Test
    fun runningVpnDisablesEditingButRetainsDraftAndAllowsCancel() {
        val canEditSettings = mutableStateOf(true)
        val profileName = mutableStateOf("Draft Profile")

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        RemoteProfileDialog(
                            profileName = profileName.value,
                            titleRes = R.string.profile_edit_settings,
                            confirmRes = R.string.save,
                            requireName = true,
                            profileUrl = "https://example.invalid/profile.yaml",
                            autoUpdate = false,
                            userAgent = "",
                            proxyUrl = "",
                            isDownloading = false,
                            canEditSettings = canEditSettings.value,
                            downloadProgress = null,
                            onProfileNameChange = { profileName.value = it },
                            onProfileUrlChange = {},
                            onAutoUpdateChange = {},
                            onUserAgentChange = {},
                            onProxyUrlChange = {},
                            onConfirm = {},
                            onDismiss = {},
                        )
                    }
                }
            }

            composeRule.onNodeWithText("Draft Profile").assertIsDisplayed().assertIsEnabled()

            scenario.onActivity { canEditSettings.value = false }
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Draft Profile").assertIsDisplayed().assertIsNotEnabled()
            composeRule.onNodeWithText(context.getString(R.string.save)).assertIsNotEnabled()
            composeRule.onNodeWithText(context.getString(android.R.string.cancel)).assertIsEnabled()
            assertEquals("Draft Profile", profileName.value)
        }
    }
}
