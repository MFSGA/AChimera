package rs.chimera.android.ui.metacubex.activity

import android.content.Intent
import android.app.job.JobScheduler
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.ProfileAutoUpdatePolicy
import rs.chimera.android.backend.ProfileAutoUpdateScheduler
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.ui.metacubex.design.MetaRemoteProfilePropertiesDesign
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class ProfilePropertiesInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val backend get() = BackendProvider.provide()

    @Test
    fun localDraftSurvivesRecreationAndSavesThroughTheBackend() = withLocalProfile { id ->
        ActivityScenario.launch<MetaProfilePropertiesActivity>(intent(id)).use { scenario ->
            awaitPage(scenario, ORIGINAL)
            scenario.onActivity { activity ->
                assertFalse(action(activity, R.id.property_url).isEnabled)
                assertFalse(action(activity, R.id.property_auto_update).isEnabled)
                assertTrue(activity.findViewById<View>(R.id.property_browse).isEnabled)
                assertEquals(View.GONE, activity.findViewById<View>(R.id.property_options).visibility)
            }
            capture(scenario, "local-before")
            editName(scenario)
            scenario.recreate()
            awaitPage(scenario, RENAMED)
            assertEquals(ORIGINAL, profileName(id))
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            capture(scenario, "local-discard-confirmation")
            onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
            awaitPage(scenario, RENAMED)
            capture(scenario, "local-draft-restored")
            save(scenario)
            await { profileName(id) == RENAMED && scenario.state == Lifecycle.State.DESTROYED }
        }
    }

    @Test
    fun dirtyDraftSurvivesConsecutiveRecreationWhileLoadingAndConfirmsExit() = withLocalProfile { id ->
        val loads = AtomicInteger()
        val release = CompletableDeferred<Unit>()
        val delegate = object : ChimeraBackend by backend {
            override suspend fun listProfiles(): List<ProfileSummary> {
                loads.incrementAndGet()
                release.await()
                return backend.listProfiles()
            }
        }
        try {
            ActivityScenario.launch<MetaProfilePropertiesActivity>(intent(id)).use { scenario ->
                awaitPage(scenario, ORIGINAL)
                editName(scenario)
                scenario.onActivity { installSaveBackend(it, delegate) }
                scenario.recreate()
                await { loads.get() == 1 }
                scenario.onActivity {
                    assertTrue(it.findViewById<View>(R.id.property_name) == null)
                    it.onBackPressedDispatcher.onBackPressed()
                }
                onView(withId(android.R.id.button2)).inRoot(isDialog()).perform(click())
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                scenario.recreate()
                await { loads.get() == 2 }
                scenario.onActivity { assertTrue(it.findViewById<View>(R.id.property_name) == null) }
                release.complete(Unit)
                awaitPage(scenario, RENAMED)
                assertEquals(ORIGINAL, profileName(id))
                capture(scenario, "local-double-recreated")
                save(scenario)
                await { profileName(id) == RENAMED && scenario.state == Lifecycle.State.DESTROYED }
            }
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun saveFailureKeepsInputAndRetryUsesTheRealBackend() = withLocalProfile { id ->
        ActivityScenario.launch<MetaProfilePropertiesActivity>(intent(id)).use { scenario ->
            awaitPage(scenario, ORIGINAL)
            val writes = AtomicInteger()
            val delegate = object : ChimeraBackend by backend {
                override suspend fun renameProfile(id: String, newName: String) {
                    if (writes.incrementAndGet() == 1) error("Synthetic write failure")
                    backend.renameProfile(id, newName)
                }
            }
            scenario.onActivity { installSaveBackend(it, delegate) }
            editName(scenario)
            save(scenario)
            awaitUi(scenario) {
                it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text?.contains("Synthetic write failure") == true
            }
            awaitPage(scenario, RENAMED)
            assertEquals(ORIGINAL, profileName(id))
            capture(scenario, "local-save-failed")
            save(scenario)
            await { profileName(id) == RENAMED && scenario.state == Lifecycle.State.DESTROYED }
            assertEquals(2, writes.get())
        }
    }

    @Test
    fun savingSurvivesRecreationWithoutDuplicatingTheWrite() = withLocalProfile { id ->
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writes = AtomicInteger()
        val delegate = object : ChimeraBackend by backend {
            override suspend fun renameProfile(id: String, newName: String) {
                writes.incrementAndGet()
                started.complete(Unit)
                release.await()
                backend.renameProfile(id, newName)
            }
        }
        try {
            ActivityScenario.launch<MetaProfilePropertiesActivity>(intent(id)).use { scenario ->
                awaitPage(scenario, ORIGINAL)
                scenario.onActivity { installSaveBackend(it, delegate) }
                editName(scenario)
                save(scenario)
                runBlocking { withTimeout(10_000) { started.await() } }
                scenario.recreate()
                awaitPage(scenario, RENAMED)
                awaitUi(scenario) { it.findViewById<View>(R.id.remote_profile_save_progress).isShown }
                scenario.onActivity {
                    assertEquals(View.GONE, it.findViewById<View>(R.id.remote_profile_save).visibility)
                    assertFalse(action(it, R.id.property_name).isEnabled)
                }
                capture(scenario, "local-saving-recreated")
                assertEquals(1, writes.get())
                release.complete(Unit)
                await { profileName(id) == RENAMED && scenario.state == Lifecycle.State.DESTROYED }
                assertEquals(1, writes.get())
            }
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun remotePropertiesSaveMetadataThroughTheRealBackendWithoutReplacingTheFile() = withProfile(remote = true) { id ->
        val original = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
        runBlocking(Dispatchers.IO) {
            backend.updateRemoteProfileSettings(
                id,
                rs.chimera.android.backend.model.RemoteProfileSettings(
                    name = original.name,
                    url = requireNotNull(original.url),
                    autoUpdate = original.autoUpdate,
                    userAgent = "fixture-agent",
                    proxyUrl = "http://127.0.0.1:7890",
                    autoUpdateIntervalMillis = original.autoUpdateIntervalMillis,
                ),
                expectedIntervalMillis = original.autoUpdateIntervalMillis,
            )
        }
        val before = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
        val bytes = File(before.filePath).readBytes()
        ActivityScenario.launch<MetaProfilePropertiesActivity>(intent(id)).use { scenario ->
            awaitPage(scenario, ORIGINAL)
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.profile_properties_screen_title),
                    activity.findViewById<TextView>(R.id.toolbar_title).text.toString(),
                )
                assertEquals(View.GONE, activity.findViewById<View>(R.id.property_options).visibility)
            }
            editName(scenario)
            scenario.onActivity { assertTrue(action(it, R.id.property_url).performClick()) }
            onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText("https://example.com/profile.yaml"), closeSoftKeyboard())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            scenario.recreate()
            awaitPage(scenario, RENAMED)
            capture(scenario, "remote-edited")
            save(scenario)
            await { profileName(id) == RENAMED && scenario.state == Lifecycle.State.DESTROYED }
        }
        val saved = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
        assertEquals("https://example.com/profile.yaml", saved.url)
        assertEquals(before.userAgent, saved.userAgent)
        assertEquals(before.proxyUrl, saved.proxyUrl)
        assertFalse(saved.autoUpdate)
        assertTrue(saved.isRemote)
        assertTrue("Editing metadata must not replace the downloaded configuration", bytes.contentEquals(File(saved.filePath).readBytes()))
    }

    @Test
    fun realBackendPreservesRetryAndPendingStateAndRejectsAnOldIntervalResult() = withProfile(remote = true, autoUpdate = true) { id ->
        val before = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
        val state = ProfileAutoUpdatePolicy.bindStateToSource(
            before,
            ProfileAutoUpdatePolicy.failureState(1, System.currentTimeMillis(), IllegalStateException())
                .copy(runtimeApplyPending = true),
        )
        assertTrue(runBlocking(Dispatchers.IO) { backend.recordProfileAutoUpdateState(id, state) })
        runBlocking(Dispatchers.IO) {
            backend.updateRemoteProfileSettings(
                id,
                rs.chimera.android.backend.model.RemoteProfileSettings(
                    before.name, before.url!!, true, autoUpdateIntervalMillis = 30L * 60_000,
                ),
                expectedIntervalMillis = before.autoUpdateIntervalMillis,
            )
        }
        val after = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
        assertEquals(before.lastUpdated, after.lastUpdated)
        assertEquals(state.failureCount, after.autoUpdateFailures)
        assertEquals(state.nextAttemptAt, after.nextAutoUpdateAt)
        assertTrue(after.runtimeApplyPending)
        assertFalse(runBlocking(Dispatchers.IO) { backend.recordProfileAutoUpdateState(id, state) })
        assertEquals(after, runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } })
    }

    @Test
    fun staleIntervalDraftIsRejectedAfterAnotherEditorAndActivityRecreation() = withProfile(remote = true, autoUpdate = true) { id ->
        val before = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
        ActivityScenario.launch<MetaProfilePropertiesActivity>(intent(id)).use { scenario ->
            awaitPage(scenario, ORIGINAL)
            editName(scenario)
            runBlocking(Dispatchers.IO) {
                backend.updateRemoteProfileSettings(
                    id,
                    rs.chimera.android.backend.model.RemoteProfileSettings(
                        before.name, before.url!!, true, autoUpdateIntervalMillis = 60L * 60_000,
                    ),
                    expectedIntervalMillis = before.autoUpdateIntervalMillis,
                )
            }
            scenario.recreate()
            awaitPage(scenario, RENAMED)
            save(scenario)
            awaitUi(scenario) {
                it.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.text
                    ?.contains(it.getString(R.string.profile_properties_changed)) == true
            }
            val after = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
            assertEquals(60L * 60_000, after.autoUpdateIntervalMillis)
            assertEquals(before.name, after.name)
            assertEquals(before.lastUpdated, after.lastUpdated)
        }
    }

    @Test
    fun remoteIntervalPersistsThroughRecreationWithoutRedownloadingAndCanBeDisabled() = withProfile(remote = true, autoUpdate = true) { id ->
        val before = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
        val bytes = File(before.filePath).readBytes()
        val interval = 30L * 60_000
        ActivityScenario.launch<MetaProfilePropertiesActivity>(intent(id)).use { scenario ->
            awaitPage(scenario, ORIGINAL)
            editInterval(scenario, "30")
            scenario.recreate()
            awaitPage(scenario, ORIGINAL)
            scenario.onActivity {
                assertEquals(it.getString(R.string.profile_properties_update_minutes, 30L),
                    it.findViewById<View>(R.id.property_auto_update).findViewById<TextView>(R.id.property_value).text.toString())
            }
            save(scenario)
            await { scenario.state == Lifecycle.State.DESTROYED }
        }
        val saved = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
        assertTrue(saved.autoUpdate)
        assertEquals(interval, saved.autoUpdateIntervalMillis)
        assertEquals(before.lastUpdated, saved.lastUpdated)
        assertEquals(before.url, saved.url)
        assertTrue(bytes.contentEquals(File(saved.filePath).readBytes()))
        val profiles = runBlocking(Dispatchers.IO) { backend.listProfiles() }
        val job = context.getSystemService(JobScheduler::class.java).allPendingJobs.single { it.id == ProfileAutoUpdateScheduler.JOB_ID }
        assertEquals(ProfileAutoUpdatePolicy.scheduleIntervalMillis(profiles), job.intervalMillis)
        ActivityScenario.launch<MetaProfilePropertiesActivity>(intent(id)).use { scenario ->
            awaitPage(scenario, ORIGINAL)
            editInterval(scenario, "")
            save(scenario)
            await { scenario.state == Lifecycle.State.DESTROYED }
        }
        val disabled = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id } }
        assertFalse(disabled.autoUpdate)
        assertEquals(interval, disabled.autoUpdateIntervalMillis)
        assertEquals(before.lastUpdated, disabled.lastUpdated)
        assertTrue(bytes.contentEquals(File(disabled.filePath).readBytes()))
    }

    private fun editInterval(scenario: ActivityScenario<MetaProfilePropertiesActivity>, minutes: String) {
        scenario.onActivity { assertTrue(action(it, R.id.property_auto_update).performClick()) }
        instrumentation.uiAutomation.waitForIdle(600, 5_000)
        onView(withId(R.id.meta_text_field)).inRoot(isDialog()).perform(replaceText(minutes), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
    }

    @Test
    fun remoteFormPreservesDownloadOptionsAndHonorsTheEditingGate() {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var page: MetaRemoteProfilePropertiesDesign
            scenario.onActivity {
                WindowCompat.setDecorFitsSystemWindows(it.window, false)
                page = MetaRemoteProfilePropertiesDesign(
                    it, "https://example.com/profile.yaml", ORIGINAL, true, "fixture-agent", "http://127.0.0.1:7890",
                    existingProfile = true,
                )
                it.setContentView(page.root)
            }
            awaitUi(scenario) { page.root.isAttachedToWindow }
            capture(scenario, "remote-ready")
            scenario.onActivity {
                page.setEditingEnabled(false)
                assertFalse(page.root.findViewById<View>(R.id.remote_profile_save).isEnabled)
                assertFalse(page.root.findViewById<View>(R.id.property_url).findViewById<View>(R.id.property_action).isEnabled)
            }
            capture(scenario, "remote-gated")
            scenario.onActivity {
                page.setEditingEnabled(true)
                assertTrue(page.root.findViewById<View>(R.id.remote_profile_save).performClick())
                val form = (page.requests.tryReceive().getOrNull() as MetaRemoteProfilePropertiesDesign.Request.Submit).form
                assertEquals("fixture-agent", form.userAgent)
                assertEquals("http://127.0.0.1:7890", form.proxyUrl)
                assertTrue(form.autoUpdate)
                page.setSubmitting(true)
                page.root.findViewById<View>(R.id.remote_profile_save).performClick()
                assertTrue(page.requests.tryReceive().isFailure)
            }
        }
    }

    private fun withLocalProfile(block: (String) -> Unit) = withProfile(remote = false, block = block)

    private fun withProfile(remote: Boolean, autoUpdate: Boolean = false, block: (String) -> Unit) {
        runBlocking(Dispatchers.IO) { backend.awaitReady() }
        assertEquals(ServiceState.STOPPED, backend.serviceState.value)
        val before = runBlocking(Dispatchers.IO) { backend.listProfiles() }
        val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val source = File.createTempFile("properties-fixture-", ".yaml", directory)
        var id: String? = null
        var server: ServerSocket? = null
        var serverThread: Thread? = null
        val serverFailure = AtomicReference<Exception?>()
        try {
            source.writeText("proxies: []\nproxy-groups:\n  - name: Manual\n    type: select\n    proxies: [DIRECT]\nrules:\n  - MATCH,DIRECT\n")
            val requestUrl = if (remote) {
                val listener = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).also {
                    it.soTimeout = 15_000
                    server = it
                }
                serverThread = thread(name = "Properties fixture HTTP") {
                    try {
                        listener.accept().use { socket ->
                            socket.soTimeout = 15_000
                            val input = socket.getInputStream().bufferedReader()
                            while (!input.readLine().isNullOrEmpty()) Unit
                            val body = source.readBytes()
                            socket.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Type: text/yaml\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(body)
                                flush()
                            }
                        }
                    } catch (error: Exception) {
                        if (!listener.isClosed) serverFailure.set(error)
                    }
                }
                "http://127.0.0.1:${listener.localPort}/profile.yaml"
            } else {
                null
            }
            id = runBlocking(Dispatchers.IO) {
                val importedName = if (requestUrl != null) {
                    backend.importRemoteProfile(RemoteProfileRequest(ORIGINAL, requestUrl, autoUpdate = autoUpdate))
                } else {
                    backend.importLocalProfile(
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source),
                        ORIGINAL,
                    )
                }
                backend.listProfiles().single { profile ->
                    profile.name == importedName && before.none { it.id == profile.id }
                }.id
            }
            assertTrue("The synthetic HTTP response must succeed", serverFailure.get() == null)
            block(id)
        } finally {
            server?.close()
            serverThread?.join(5_000)
            id?.let { runBlocking(Dispatchers.IO) { backend.deleteProfile(it) } }
            assertTrue(source.delete())
            val after = runBlocking(Dispatchers.IO) { backend.listProfiles() }
            assertTrue("Original profile catalog must be preserved", before == after)
        }
    }

    private fun intent(id: String) = Intent(context, MetaProfilePropertiesActivity::class.java)
        .putExtra(MetaProfilePropertiesActivity.EXTRA_PROFILE_ID, id)

    private fun profileName(id: String) = runBlocking(Dispatchers.IO) { backend.listProfiles().first { it.id == id }.name }

    private fun action(activity: MetaProfilePropertiesActivity, id: Int): View =
        activity.findViewById<View>(id).findViewById(R.id.property_action)

    private fun editName(scenario: ActivityScenario<MetaProfilePropertiesActivity>) {
        scenario.onActivity { assertTrue(action(it, R.id.property_name).performClick()) }
        onView(isAssignableFrom(EditText::class.java)).inRoot(isDialog()).perform(replaceText(RENAMED), closeSoftKeyboard())
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        awaitPage(scenario, RENAMED)
    }

    private fun save(scenario: ActivityScenario<MetaProfilePropertiesActivity>) =
        scenario.onActivity { assertTrue(it.findViewById<View>(R.id.remote_profile_save).performClick()) }

    private fun installSaveBackend(activity: MetaProfilePropertiesActivity, delegate: ChimeraBackend) {
        val model = ViewModelProvider(activity)[MetaProfilePropertiesViewModel::class.java]
        MetaProfilePropertiesViewModel::class.java.getDeclaredField("backend").apply { isAccessible = true }.set(model, delegate)
    }

    private fun awaitPage(scenario: ActivityScenario<MetaProfilePropertiesActivity>, name: String) = awaitUi(scenario) {
        it.findViewById<View>(R.id.property_name)?.findViewById<TextView>(R.id.property_value)?.text?.toString() == name
    }

    private fun <A : androidx.activity.ComponentActivity> awaitUi(scenario: ActivityScenario<A>, predicate: (A) -> Boolean) = await {
        var ready = false
        scenario.onActivity { ready = predicate(it) }
        ready
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (predicate()) return
            SystemClock.sleep(20)
        }
        throw AssertionError("Properties must reach the expected saved/draft state")
    }

    private fun <A : androidx.activity.ComponentActivity> capture(scenario: ActivityScenario<A>, name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureProperties") != "true") return
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        if (name == "local-discard-confirmation") {
            onView(withId(android.R.id.button1)).inRoot(isDialog()).check { view, error ->
                if (error != null) throw error
                val panel = view.rootView.findViewById<View>(androidx.appcompat.R.id.parentPanel)
                val location = IntArray(2)
                panel.getLocationOnScreen(location)
                val bounds = JSONObject().put("x", location[0]).put("y", location[1]).put("width", panel.width).put("height", panel.height)
                File(context.cacheDir, "properties-$name.json").writeText(JSONObject().put("panel", bounds).toString())
            }
        }
        val committed = CountDownLatch(1)
        scenario.onActivity {
            val toolbar = it.findViewById<View>(R.id.remote_profile_toolbar)
            val bars = checkNotNull(ViewCompat.getRootWindowInsets(toolbar)).getInsets(WindowInsetsCompat.Type.systemBars())
            assertEquals(bars.top, toolbar.paddingTop)
            toolbar.rootView.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            toolbar.rootView.invalidate()
        }
        assertTrue(committed.await(5, TimeUnit.SECONDS))
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(context.cacheDir, "properties-$name.png").outputStream().use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val ORIGINAL = "CMFA UI fixture"
        const val RENAMED = "CMFA UI renamed"
    }
}
