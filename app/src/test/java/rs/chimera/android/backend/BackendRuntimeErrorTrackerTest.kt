package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.BackendRuntimeErrorSource

class BackendRuntimeErrorTrackerTest {
    @Test
    fun recordUsesFormattedDetail() {
        val tracker = BackendRuntimeErrorTracker { "clean detail" }

        tracker.record(
            source = BackendRuntimeErrorSource.PROXY_GROUPS,
            prefix = "Controller failed",
            error = IllegalStateException("sensitive detail"),
        )

        assertEquals(BackendRuntimeErrorSource.PROXY_GROUPS, tracker.error.value?.source)
        assertEquals("Controller failed: clean detail", tracker.error.value?.message)
    }

    @Test
    fun clearOnlyRemovesMatchingSource() {
        val tracker = BackendRuntimeErrorTracker { "detail" }
        tracker.record(
            source = BackendRuntimeErrorSource.TRAFFIC,
            prefix = "Telemetry failed",
            error = IllegalStateException(),
        )

        tracker.clear(BackendRuntimeErrorSource.PROXY_GROUPS)
        assertEquals(BackendRuntimeErrorSource.TRAFFIC, tracker.error.value?.source)

        tracker.clear(BackendRuntimeErrorSource.TRAFFIC)
        assertNull(tracker.error.value)
    }
}
