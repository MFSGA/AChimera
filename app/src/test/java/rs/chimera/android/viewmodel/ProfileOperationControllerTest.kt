package rs.chimera.android.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileOperationControllerTest {
    @Test
    fun `begin publishes operation state and rejects overlap`() {
        val controller = ProfileOperationController()

        assertTrue(controller.tryBegin(ProfileOperationKind.DOWNLOADING))
        assertTrue(controller.state.isDownloading)
        assertTrue(controller.state.isInProgress)
        assertFalse(controller.tryBegin(ProfileOperationKind.REFRESHING))
        assertTrue(controller.state.isDownloading)
    }

    @Test
    fun `end clears presentation state and allows next operation`() {
        val controller = ProfileOperationController()

        assertTrue(controller.tryBegin(ProfileOperationKind.VERIFYING))
        controller.end()

        assertFalse(controller.state.isInProgress)
        assertTrue(controller.tryBegin(ProfileOperationKind.REFRESHING))
        assertTrue(controller.state.isRefreshingRemoteProfiles)
    }
}
