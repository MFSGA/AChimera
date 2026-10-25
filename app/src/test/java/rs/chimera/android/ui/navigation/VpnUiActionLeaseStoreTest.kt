package rs.chimera.android.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnUiActionLeaseStoreTest {
    @Test
    fun staleLeaseCannotReleaseActionResumedAfterActivityRecreation() {
        val store = VpnUiActionLeaseStore()
        val original = store.tryBegin("pending-vpn-action", vpnTransitioning = false)
        val resumed = store.resume("pending-vpn-action")

        assertNotNull(original)
        assertNotNull(resumed)
        assertTrue(store.isActive)
        assertFalse(store.finish(original!!))
        assertTrue(store.isActive)
        assertTrue(store.finish(resumed!!))
        assertFalse(store.isActive)
    }

    @Test
    fun anotherActionCannotAcquireWhileVpnActionIsPending() {
        val store = VpnUiActionLeaseStore()
        val active = store.tryBegin("first", vpnTransitioning = false)

        assertNotNull(active)
        assertNull(store.tryBegin("second", vpnTransitioning = false))
        assertNull(store.resume("second"))
        assertTrue(store.isActive)
    }

    @Test
    fun newActionCannotBeginDuringBackendTransition() {
        val store = VpnUiActionLeaseStore()

        assertNull(store.tryBegin("first", vpnTransitioning = true))
        assertFalse(store.isActive)
    }
}
