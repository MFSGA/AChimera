package rs.chimera.android.ui.metacubex.design

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LogcatStreamingDesignInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun referenceRowsAndToolbarRequestsDoNotRequireVpnOrLogFileChanges() = withDesign { scenario, design ->
        scenario.onActivity { design.showContent(fixture) }
        awaitRow(scenario, design, 2, fixture[2].message)
        scenario.onActivity {
            val recycler = design.root.findViewById<RecyclerView>(R.id.log_recycler_list)
            val bars = checkNotNull(ViewCompat.getRootWindowInsets(design.root))
                .getInsets(WindowInsetsCompat.Type.systemBars())
            val toolbar = design.root.findViewById<View>(R.id.log_activity_bar)
            assertEquals("The toolbar must receive the real status-bar inset after attachment", bars.top, toolbar.paddingTop)
            assertEquals(toolbar.height, recycler.paddingTop)
            val manager = recycler.layoutManager as LinearLayoutManager
            assertTrue(manager.reverseLayout)
            assertTrue(manager.stackFromEnd)
            val row = recycler.findViewHolderForAdapterPosition(2)!!.itemView
            assertEquals("Debug", row.findViewById<TextView>(R.id.log_message_level).text.toString())
            assertEquals("12:34:58.345", row.findViewById<TextView>(R.id.log_message_time).text.toString())
            row.performClick()
            assertTrue(design.requests.tryReceive().isFailure)
            assertTrue(row.performLongClick())
            assertEquals(LogsDesign.Request.CopyMessage(fixture[2].message), design.requests.tryReceive().getOrNull())
            design.root.findViewById<View>(R.id.log_close_button).performClick()
            assertEquals(LogsDesign.Request.Close, design.requests.tryReceive().getOrNull())
            design.root.findViewById<View>(R.id.log_back_button).performClick()
            assertEquals(LogsDesign.Request.NavigateBack, design.requests.tryReceive().getOrNull())
        }
        captureFixture(scenario, design, "content", fixture.size)
        scenario.onActivity { design.showContent(emptyList()) }
        awaitCount(scenario, design, 0)
        captureFixture(scenario, design, "empty", 0)
    }

    @Test
    fun loadingErrorAndRetryDoNotHideTheCloseActions() = withDesign { scenario, design ->
        scenario.onActivity {
            design.showLoading()
            assertEquals(View.VISIBLE, design.root.findViewById<View>(R.id.state_progress).visibility)
            assertEquals(View.GONE, design.root.findViewById<View>(R.id.log_recycler_list).visibility)
            design.showError("Fixture read error")
            assertEquals(View.GONE, design.root.findViewById<View>(R.id.state_progress).visibility)
            assertEquals("Fixture read error", design.root.findViewById<TextView>(R.id.state_message).text.toString())
            val retry = design.root.findViewById<View>(R.id.retry_button)
            assertEquals(View.VISIBLE, retry.visibility)
            retry.performClick()
            assertEquals(LogsDesign.Request.Retry, design.requests.tryReceive().getOrNull())
            assertEquals(View.VISIBLE, design.root.findViewById<View>(R.id.log_activity_bar).visibility)
        }
    }

    @Test
    fun aPendingDifferentSnapshotCannotReplaceTheLatestRequestedOriginalSnapshot() = withDesign { scenario, design ->
        scenario.onActivity { design.showContent(fixture) }
        awaitRow(scenario, design, 2, fixture[2].message)
        scenario.onActivity {
            design.showContent(listOf(RuntimeLogEntry("temporary snapshot")))
            design.showContent(fixture)
        }
        awaitDifferWork()
        awaitCount(scenario, design, fixture.size)
        instrumentation.waitForIdleSync()
        awaitRow(scenario, design, 2, fixture[2].message)
    }

    @Test
    fun newMessagesFollowOnlyWhenReadingTheLatestAndNeverInterruptADrag() = withDesign { scenario, design ->
        val messages = (0 until 100).map { RuntimeLogEntry("Public fixture log $it") }
        scenario.onActivity { design.showContent(messages) }
        awaitRow(scenario, design, 99, messages[99].message)
        scenario.onActivity {
            design.root.findViewById<RecyclerView>(R.id.log_recycler_list).scrollToPosition(20)
        }
        awaitRow(scenario, design, 20, messages[20].message)
        scenario.onActivity {
            val recycler = design.root.findViewById<RecyclerView>(R.id.log_recycler_list)
            assertTrue("Reading older rows must leave the latest position", recycler.computeVerticalScrollOffset() > 0)
            design.showContent(messages + RuntimeLogEntry("Appended public fixture 100"))
        }
        awaitDifferWork()
        awaitCount(scenario, design, 101)
        scenario.onActivity {
            val recycler = design.root.findViewById<RecyclerView>(R.id.log_recycler_list)
            assertTrue("An update must not pull older history back to the latest row", recycler.computeVerticalScrollOffset() > 0)
            recycler.scrollToPosition(100)
        }
        awaitRow(scenario, design, 100, "Appended public fixture 100")
        val updated = messages + RuntimeLogEntry("Appended public fixture 100") + RuntimeLogEntry("Appended public fixture 101")
        scenario.onActivity { design.showContent(updated) }
        awaitRow(scenario, design, 101, "Appended public fixture 101")
        scenario.onActivity {
            val recycler = design.root.findViewById<RecyclerView>(R.id.log_recycler_list)
            design.showContent(updated + RuntimeLogEntry("Appended during drag"))
            val downTime = SystemClock.uptimeMillis()
            listOf(MotionEvent.ACTION_DOWN to 900f, MotionEvent.ACTION_MOVE to 600f).forEachIndexed { i, (action, y) ->
                val event = MotionEvent.obtain(downTime, downTime + i * 16, action, 540f, y, 0)
                try {
                    recycler.dispatchTouchEvent(event)
                } finally {
                    event.recycle()
                }
            }
            assertEquals(RecyclerView.SCROLL_STATE_DRAGGING, recycler.scrollState)
            val up = MotionEvent.obtain(downTime, downTime + 32, MotionEvent.ACTION_UP, 540f, 600f, 0)
            try {
                recycler.dispatchTouchEvent(up)
            } finally {
                up.recycle()
            }
            recycler.stopScroll()
        }
        awaitDifferWork()
        awaitCount(scenario, design, 103)
        scenario.onActivity {
            assertTrue(
                "A diff committed after a user drag must not force a jump to the latest row",
                design.root.findViewById<RecyclerView>(R.id.log_recycler_list).computeVerticalScrollOffset() > 0,
            )
        }
    }

    private fun withDesign(block: (ActivityScenario<MetaMainActivity>, LogsDesign) -> Unit) {
        ActivityScenario.launch(MetaMainActivity::class.java).use { scenario ->
            lateinit var design: LogsDesign
            scenario.onActivity { activity ->
                design = LogsDesign(activity)
                activity.setContentView(design.root)
            }
            block(scenario, design)
        }
    }

    private fun awaitCount(scenario: ActivityScenario<MetaMainActivity>, design: LogsDesign, expected: Int) {
        awaitCondition(scenario) {
            design.root.findViewById<RecyclerView>(R.id.log_recycler_list).adapter?.itemCount == expected
        }
    }

    private fun awaitRow(
        scenario: ActivityScenario<MetaMainActivity>,
        design: LogsDesign,
        position: Int,
        message: String,
    ) {
        awaitCondition(scenario) {
            design.root.findViewById<RecyclerView>(R.id.log_recycler_list)
                .findViewHolderForAdapterPosition(position)?.itemView
                ?.findViewById<TextView>(R.id.log_message_content)?.text?.toString() == message
        }
    }

    private fun awaitCondition(scenario: ActivityScenario<MetaMainActivity>, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        var matched = false
        while (!matched && SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { matched = condition() }
            if (!matched) SystemClock.sleep(20)
        }
        assertTrue("The submitted log rows should render", matched)
    }

    private fun awaitDifferWork() {
        // AsyncDifferConfig shares a two-worker default executor. Occupy both
        // only after earlier diffs finish, then drain their main-thread commits.
        val executor = AsyncDifferConfig.Builder(object : DiffUtil.ItemCallback<RuntimeLogEntry>() {
            override fun areItemsTheSame(oldItem: RuntimeLogEntry, newItem: RuntimeLogEntry) = oldItem == newItem
            override fun areContentsTheSame(oldItem: RuntimeLogEntry, newItem: RuntimeLogEntry) = oldItem == newItem
        }).build().backgroundThreadExecutor
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        try {
            repeat(2) {
                executor.execute {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
            }
            assertTrue("Pending list diffs should finish", entered.await(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
        }
        instrumentation.waitForIdleSync()
    }

    private fun captureFixture(
        scenario: ActivityScenario<MetaMainActivity>,
        design: LogsDesign,
        state: String,
        expectedRows: Int,
    ) {
        if (InstrumentationRegistry.getArguments().getString("captureFixtures") != "true") return
        awaitCondition(scenario) {
            val recycler = design.root.findViewById<RecyclerView>(R.id.log_recycler_list)
            recycler.adapter?.itemCount == expectedRows &&
                recycler.childCount == expectedRows &&
                !recycler.hasPendingAdapterUpdates() &&
                !recycler.isComputingLayout &&
                !recycler.isAnimating
        }
        val committed = CountDownLatch(1)
        scenario.onActivity {
            val view = design.root
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && view.isHardwareAccelerated) {
                view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                view.invalidate()
            } else {
                view.postOnAnimation { view.postOnAnimation { committed.countDown() } }
            }
        }
        assertTrue("The settled log layout must reach a rendered frame before capture", committed.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        val context = instrumentation.targetContext
        val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val file = File(context.cacheDir, "logcat-fixture-${if (dark) "dark" else "light"}-$state.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        try {
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
        assertFalse(file.length() == 0L)
    }

    private val fixture = listOf(
        RuntimeLogEntry("UI alignment fixture: service ready", "12:34:56.789", "Info"),
        RuntimeLogEntry(
            "UI alignment fixture: a longer diagnostic message that wraps onto the next line at this display width.",
            "12:34:57.012",
            "Warning",
        ),
        RuntimeLogEntry("UI alignment fixture: waiting for the next message", "12:34:58.345", "Debug"),
    )
}
