package rs.chimera.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.service.VpnDesiredStateReason
import rs.chimera.android.service.VpnDesiredStateStore

@RunWith(AndroidJUnit4::class)
class VpnDesiredStateStoreInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    @After
    fun clearStore() {
        context.getSharedPreferences(VpnDesiredStateStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun desiredStatePersistsAcrossStoreInstances() {
        VpnDesiredStateStore(context, now = { 1_000 }).markRunning()

        val running = VpnDesiredStateStore(context).snapshot()
        assertTrue(running.shouldRun)
        assertEquals(1_000, running.updatedAt)
        assertEquals(VpnDesiredStateReason.USER_START, running.reason)

        VpnDesiredStateStore(context, now = { 2_000 }).markStopped(
            VpnDesiredStateReason.USER_STOP,
        )

        val stopped = VpnDesiredStateStore(context).snapshot()
        assertFalse(stopped.shouldRun)
        assertEquals(2_000, stopped.updatedAt)
        assertEquals(VpnDesiredStateReason.USER_STOP, stopped.reason)
    }

    @Test
    fun invalidPersistedReasonFallsBackToUserStop() {
        context.getSharedPreferences(VpnDesiredStateStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(VpnDesiredStateStore.KEY_SHOULD_RUN, false)
            .putLong(VpnDesiredStateStore.KEY_UPDATED_AT, 3_000)
            .putString(VpnDesiredStateStore.KEY_REASON, "UNKNOWN")
            .commit()

        val snapshot = VpnDesiredStateStore(context).snapshot()

        assertFalse(snapshot.shouldRun)
        assertEquals(3_000, snapshot.updatedAt)
        assertEquals(VpnDesiredStateReason.USER_STOP, snapshot.reason)
    }
}
