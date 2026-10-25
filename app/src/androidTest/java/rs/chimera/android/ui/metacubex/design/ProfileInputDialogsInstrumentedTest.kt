package rs.chimera.android.ui.metacubex.design

import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.ProfileUpdateInterval
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ProfileInputDialogsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun nameInputSelectsAllRejectsBlankAndCancelKeepsTheOriginal() = withPage { scenario, page ->
        val field = open(scenario, page, R.id.property_name)
        scenario.onActivity {
            assertEquals(0, field.selectionStart)
            assertEquals(NAME.length, field.selectionEnd)
            assertTrue(layout(field).isErrorEnabled)
            assertEquals(it.getString(R.string.profile_properties_title), layout(field).hint.toString())
        }
        capture("name-ready", field)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText("   "))
        scenario.onActivity {
            assertEquals(it.getString(R.string.profile_properties_name_input_error), layout(field).error.toString())
            assertFalse(positive(field).isEnabled)
        }
        capture("name-invalid", field)
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        scenario.onActivity {
            assertEquals(NAME, value(page, R.id.property_name))
            assertFalse(page.hasChanges())
        }
        open(scenario, page, R.id.property_name)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText("CMFA UI renamed"), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        scenario.onActivity {
            assertEquals("CMFA UI renamed", value(page, R.id.property_name))
            assertTrue(page.hasChanges())
        }
    }

    @Test
    fun urlInputKeepsPrivacyFlagsValidatesAndOnlyConfirmsValidHttpUrls() = withPage { scenario, page ->
        var field = open(scenario, page, R.id.property_url)
        scenario.onActivity {
            assertTrue(field.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0)
            assertTrue(field.inputType and InputType.TYPE_TEXT_VARIATION_URI != 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) assertEquals(View.IMPORTANT_FOR_AUTOFILL_NO, field.importantForAutofill)
        }
        capture("url-ready", field)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText("ftp://example.com/profile.yaml"))
        scenario.onActivity {
            assertEquals(it.getString(R.string.profile_properties_url_placeholder), layout(field).error.toString())
            assertFalse(positive(field).isEnabled)
        }
        capture("url-invalid", field)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText("https://"))
        scenario.onActivity { assertFalse(positive(field).isEnabled) }
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        scenario.onActivity {
            assertEquals(URL, value(page, R.id.property_url))
            assertFalse(page.hasChanges())
        }
        field = open(scenario, page, R.id.property_url)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText("https://example.com/changed.yaml"), closeSoftKeyboard())
        scenario.onActivity {
            assertEquals("https://example.com/changed.yaml", field.text.toString())
            assertTrue(positive(field).isEnabled)
        }
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        await { !field.isAttachedToWindow }
        scenario.onActivity {
            assertEquals("https://example.com/changed.yaml", value(page, R.id.property_url))
            page.root.findViewById<View>(R.id.remote_profile_save).performClick()
            val form = (page.requests.tryReceive().getOrNull() as MetaRemoteProfilePropertiesDesign.Request.Submit).form
            assertEquals("https://example.com/changed.yaml", form.url)
        }
    }

    @Test
    fun replacingTheHostOrDisablingEditingDismissesUnconfirmedInput() = withPage { scenario, page ->
        var field = open(scenario, page, R.id.property_name)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText("unconfirmed"))
        scenario.onActivity { page.setEditingEnabled(false) }
        await { !field.isAttachedToWindow }
        scenario.onActivity {
            assertFalse(page.hasChanges())
            page.setEditingEnabled(true)
        }
        field = open(scenario, page, R.id.property_name)
        scenario.onActivity { page.setSubmitting(true) }
        await { !field.isAttachedToWindow }
        scenario.onActivity { page.setSubmitting(false) }
        field = open(scenario, page, R.id.property_name)
        scenario.onActivity { it.setContentView(FrameLayout(it)) }
        await { !field.isAttachedToWindow }
        scenario.onActivity {
            assertFalse(page.hasChanges())
            assertTrue(page.requests.tryReceive().isFailure)
        }
    }

    @Test
    fun intervalUsesMinutesRejectsInvalidValuesAndPreservesDraftAcrossRecreation() = withPage(true, 30L * 60_000) { scenario, page ->
        val field = open(scenario, page, R.id.property_auto_update)
        scenario.onActivity {
            assertEquals("30", field.text.toString())
            assertEquals(0, field.selectionStart)
            assertEquals(2, field.selectionEnd)
        }
        capture("interval-ready", field)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText("14"))
        scenario.onActivity { assertFalse(positive(field).isEnabled) }
        capture("interval-invalid", field)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText(Long.MAX_VALUE.toString()))
        scenario.onActivity { assertFalse(positive(field).isEnabled) }
        onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
        scenario.onActivity { assertFalse(page.hasChanges()) }
        open(scenario, page, R.id.property_auto_update)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText(""), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        scenario.onActivity { assertEquals(it.getString(R.string.profile_properties_auto_update_disabled), value(page, R.id.property_auto_update)) }
        val empty = open(scenario, page, R.id.property_auto_update)
        capture("interval-disabled", empty)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText("15"), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        scenario.onActivity {
            val state = Bundle().also(page::saveState)
            val restored = MetaRemoteProfilePropertiesDesign(it, URL, NAME, restoredState = state, existingProfile = true)
            it.setContentView(restored.root)
            assertTrue(restored.hasChanges())
            assertEquals(it.getString(R.string.profile_properties_update_minutes, 15L), value(restored, R.id.property_auto_update))
            restored.root.findViewById<View>(R.id.remote_profile_save).performClick()
            val form = (restored.requests.tryReceive().getOrNull() as MetaRemoteProfilePropertiesDesign.Request.Submit).form
            assertTrue(form.autoUpdate)
            assertEquals(ProfileUpdateInterval.MIN_MILLIS, form.autoUpdateIntervalMillis)
        }
    }

    private fun withPage(
        autoUpdate: Boolean = false,
        intervalMillis: Long = ProfileUpdateInterval.DEFAULT_MILLIS,
        block: (ActivityScenario<MetaMainActivity>, MetaRemoteProfilePropertiesDesign) -> Unit,
    ) {
        val backend = BackendProvider.provide()
        runBlocking(Dispatchers.IO) { backend.awaitReady() }
        assertEquals(ServiceState.STOPPED, backend.serviceState.value)
        val before = runBlocking(Dispatchers.IO) { backend.listProfiles() }
        try {
            ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
                lateinit var page: MetaRemoteProfilePropertiesDesign
                scenario.onActivity {
                    WindowCompat.setDecorFitsSystemWindows(it.window, false)
                    page = MetaRemoteProfilePropertiesDesign(
                        it, URL, NAME, autoUpdate, existingProfile = true, initialAutoUpdateIntervalMillis = intervalMillis,
                    )
                    it.setContentView(page.root)
                }
                await { page.root.isAttachedToWindow && page.root.width > 0 }
                block(scenario, page)
            }
        } finally {
            assertTrue("Dialogs must not write profile data", before == runBlocking(Dispatchers.IO) { backend.listProfiles() })
            assertEquals(ServiceState.STOPPED, backend.serviceState.value)
        }
    }

    private fun open(scenario: ActivityScenario<MetaMainActivity>, page: MetaRemoteProfilePropertiesDesign, row: Int): EditText {
        scenario.onActivity { assertTrue(page.root.findViewById<View>(row).findViewById<View>(R.id.property_action).performClick()) }
        instrumentation.uiAutomation.waitForIdle(600, 5_000)
        lateinit var field: EditText
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).check { view, error ->
            if (error != null) throw error
            field = view as EditText
        }
        return field
    }

    private fun layout(field: EditText): TextInputLayout = field.rootView.findViewById(R.id.meta_text_layout)
    private fun positive(field: EditText): View = field.rootView.findViewById(android.R.id.button1)
    private fun value(page: MetaRemoteProfilePropertiesDesign, row: Int): String =
        page.root.findViewById<View>(row).findViewById<TextView>(R.id.property_value).text.toString()

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(20)
        }
        throw AssertionError("Input dialog must reach its expected state")
    }

    private fun capture(name: String, field: EditText) {
        if (InstrumentationRegistry.getArguments().getString("captureInputDialogs") != "true") return
        instrumentation.uiAutomation.waitForIdle(600, 5_000)
        val committed = CountDownLatch(1)
        val location = IntArray(2)
        val bounds = JSONObject()
        instrumentation.runOnMainSync {
            val root = field.rootView
            root.getLocationOnScreen(location)
            bounds.put("x", location[0]).put("y", location[1]).put("width", root.width).put("height", root.height)
            val panel = root.findViewById<View>(androidx.appcompat.R.id.parentPanel)
            panel.getLocationOnScreen(location)
            bounds.put("panel", JSONObject().put("x", location[0]).put("y", location[1]).put("width", panel.width).put("height", panel.height))
            root.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            root.invalidate()
        }
        assertTrue(committed.await(5, TimeUnit.SECONDS))
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val context = instrumentation.targetContext
        val theme = if (context.resources.configuration.uiMode and 48 == 32) "dark" else "light"
        try {
            File(context.cacheDir, "input-$theme-$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            File(context.cacheDir, "input-$theme-$name.json").writeText(bounds.toString())
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val NAME = "CMFA UI fixture"
        const val URL = "https://example.com/profile.yaml"
    }
}
