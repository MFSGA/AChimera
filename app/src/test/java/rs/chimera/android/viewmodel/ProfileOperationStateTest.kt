package rs.chimera.android.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileOperationStateTest {
    @Test
    fun activeOperationPublishesProgressAndKindTogether() {
        val state = ProfileOperationState.active(ProfileOperationKind.DOWNLOADING)

        assertTrue(state.isInProgress)
        assertTrue(state.isDownloading)
        assertFalse(state.isImporting)
        assertFalse(state.isRefreshingRemoteProfiles)
    }

    @Test
    fun idleOperationClearsAllProgressFlags() {
        val state = ProfileOperationState()

        assertFalse(state.isInProgress)
        assertFalse(state.isDownloading)
        assertFalse(state.isImporting)
        assertFalse(state.isRefreshingRemoteProfiles)
    }
}
