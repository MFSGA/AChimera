package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class ProfileBatchUpdateTest {
    @Test
    fun continuesAfterIndividualFailuresAndReloadsUpdatedActiveProfileOnce() = runBlocking {
        val updated = mutableListOf<String>()
        var restarts = 0

        val result = updateRemoteProfilesBatch(
            profileIds = listOf("one", "active", "bad", "active"),
            updateProfile = { id ->
                if (id == "bad") throw IllegalStateException("download failed")
                updated += id
            },
            activeProfileId = { "active" },
            serviceState = { ServiceState.RUNNING },
            restartVpn = { restarts++ },
        )

        assertEquals(listOf("one", "active"), updated)
        assertEquals(2, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(ProfileUpdateRuntimeApplyResult.Reloaded, result.runtimeApply)
        assertEquals(1, restarts)
    }

    @Test
    fun failedActiveProfileDoesNotReloadVpn() = runBlocking {
        var restarts = 0

        val result = updateRemoteProfilesBatch(
            profileIds = listOf("active", "other"),
            updateProfile = { id ->
                if (id == "active") throw IllegalStateException("failed")
            },
            activeProfileId = { "active" },
            serviceState = { ServiceState.RUNNING },
            restartVpn = { restarts++ },
        )

        assertEquals(1, result.succeeded)
        assertEquals(1, result.failed)
        assertEquals(ProfileUpdateRuntimeApplyResult.NotRequired, result.runtimeApply)
        assertEquals(0, restarts)
    }

    @Test
    fun restartFailureIsReportedSeparatelyFromProfileUpdateCounts() = runBlocking {
        val expected = IllegalStateException("reload failed")

        val result = updateRemoteProfilesBatch(
            profileIds = listOf("active"),
            updateProfile = {},
            activeProfileId = { "active" },
            serviceState = { ServiceState.RUNNING },
            restartVpn = { throw expected },
        )

        assertEquals(1, result.succeeded)
        assertEquals(0, result.failed)
        assertTrue(result.runtimeApply is ProfileUpdateRuntimeApplyResult.Failed)
        assertSame(expected, (result.runtimeApply as ProfileUpdateRuntimeApplyResult.Failed).error)
    }

    @Test
    fun updateCancellationPropagatesImmediately() = runBlocking {
        val attempted = mutableListOf<String>()

        val error = runCatching {
            updateRemoteProfilesBatch(
                profileIds = listOf("one", "cancelled", "never"),
                updateProfile = { id ->
                    attempted += id
                    if (id == "cancelled") throw CancellationException("cancelled")
                },
                activeProfileId = { null },
                serviceState = { ServiceState.STOPPED },
                restartVpn = {},
            )
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(listOf("one", "cancelled"), attempted)
    }
}
