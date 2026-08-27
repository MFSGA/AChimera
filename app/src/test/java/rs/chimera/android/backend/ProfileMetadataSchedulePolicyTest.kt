package rs.chimera.android.backend

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileMetadataSchedulePolicyTest {
    @Test
    fun changedEnabledSettingsRequestImmediateRefreshAfterSuccessfulSync() {
        val actions = profileMetadataScheduleActions(
            autoUpdateEnabled = true,
            invalidatesAutoUpdateState = true,
        )

        assertTrue(actions.requestImmediateAfterRefresh)
        assertTrue(actions.requestImmediateAfterFailure)
    }

    @Test
    fun unchangedEnabledSettingsSkipImmediateRefreshAfterSuccessfulSync() {
        val actions = profileMetadataScheduleActions(
            autoUpdateEnabled = true,
            invalidatesAutoUpdateState = false,
        )

        assertFalse(actions.requestImmediateAfterRefresh)
        assertTrue(actions.requestImmediateAfterFailure)
    }

    @Test
    fun disabledSettingsStillRecoverFailedScheduleSync() {
        val actions = profileMetadataScheduleActions(
            autoUpdateEnabled = false,
            invalidatesAutoUpdateState = true,
        )

        assertFalse(actions.requestImmediateAfterRefresh)
        assertTrue(actions.requestImmediateAfterFailure)
    }
}
