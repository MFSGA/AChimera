package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class ProfileDeletionRuntimePolicyTest {
    @Test
    fun activeProfileCanBeDeletedOnlyAfterVpnIsStoppedAndNoRestoreIsPending() {
        assertEquals(
            null,
            ProfileDeletionRuntimePolicy.blockReason(
                targetIsActive = true,
                serviceState = ServiceState.STOPPED,
                hasRunRequest = false,
            ),
        )
        assertEquals(
            ProfileDeletionRuntimePolicy.BlockReason.ACTIVE_PROFILE_IN_USE,
            ProfileDeletionRuntimePolicy.blockReason(
                targetIsActive = true,
                serviceState = ServiceState.STOPPED,
                hasRunRequest = true,
            ),
        )
    }

    @Test
    fun activeProfileCannotBeDeletedDuringAnyVpnTransitionOrRuntimeState() {
        assertEquals(
            ProfileDeletionRuntimePolicy.BlockReason.ACTIVE_PROFILE_IN_USE,
            ProfileDeletionRuntimePolicy.blockReason(
                targetIsActive = true,
                serviceState = ServiceState.RUNNING,
                hasRunRequest = true,
            ),
        )
        assertEquals(
            ProfileDeletionRuntimePolicy.BlockReason.ACTIVE_PROFILE_IN_USE,
            ProfileDeletionRuntimePolicy.blockReason(
                targetIsActive = true,
                serviceState = ServiceState.ERROR,
                hasRunRequest = false,
            ),
        )
        listOf(ServiceState.STARTING, ServiceState.STOPPING).forEach { state ->
            assertEquals(
                "$state must protect the active profile",
                ProfileDeletionRuntimePolicy.BlockReason.VPN_TRANSITION,
                ProfileDeletionRuntimePolicy.blockReason(
                    targetIsActive = true,
                    serviceState = state,
                    hasRunRequest = false,
                ),
            )
        }
    }

    @Test
    fun inactiveProfileCanBeDeletedWhileVpnIsRunning() {
        assertEquals(
            null,
            ProfileDeletionRuntimePolicy.blockReason(
                targetIsActive = false,
                serviceState = ServiceState.RUNNING,
                hasRunRequest = true,
            ),
        )
    }

    @Test
    fun profileDeletionWaitsForVpnTransitionsEvenWhenTargetIsInactive() {
        listOf(ServiceState.STARTING, ServiceState.STOPPING).forEach { state ->
            assertEquals(
                ProfileDeletionRuntimePolicy.BlockReason.VPN_TRANSITION,
                ProfileDeletionRuntimePolicy.blockReason(
                    targetIsActive = false,
                    serviceState = state,
                    hasRunRequest = false,
                ),
            )
        }
    }
}
