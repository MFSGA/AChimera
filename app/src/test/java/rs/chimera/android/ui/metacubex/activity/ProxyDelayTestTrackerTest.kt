package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyDelayTestTrackerTest {
    @Test
    fun differentGroupsCanRunWhileDuplicateGroupRequestsAreRejected() {
        val tracker = ProxyDelayTestTracker()

        assertTrue(tracker.start("group-a", total = 3))
        assertFalse(tracker.start("group-a", total = 2))
        assertTrue(tracker.start("group-b", total = 2))
        assertTrue(tracker.isTesting)
    }

    @Test
    fun finishingOneGroupKeepsOtherGroupProgressActive() {
        val tracker = ProxyDelayTestTracker()
        tracker.start("group-a", total = 3)
        tracker.start("group-b", total = 2)

        assertTrue(tracker.update("group-b", current = 2, total = 2))
        assertEquals(ProxyDelayTestProgress(current = 2, total = 2), tracker.progress("group-b"))
        assertTrue(tracker.finish("group-a"))
        assertFalse(tracker.finish("group-a"))
        assertTrue(tracker.isTesting)
        assertFalse(tracker.finish("group-c"))
        assertTrue(tracker.finish("group-b"))
        assertFalse(tracker.isTesting)
        assertEquals(null, tracker.progress("group-b"))
    }

    @Test
    fun rejectsInvalidStartAndIgnoresUpdatesForInactiveGroups() {
        val tracker = ProxyDelayTestTracker()

        assertFalse(tracker.start("", total = 1))
        assertFalse(tracker.start("group-a", total = 0))
        assertFalse(tracker.update("group-a", current = 1, total = 1))
        assertFalse(tracker.isTesting)
    }

    @Test
    fun onlyShowsCompletionNoticeWhenAtLeastOneNodeFails() {
        assertFalse(shouldShowProxyDelayFailureNotice(failureCount = 0))
        assertTrue(shouldShowProxyDelayFailureNotice(failureCount = 1))
    }
}
