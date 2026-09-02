package rs.chimera.android.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import rs.chimera.android.backend.ProfileUpdateRuntimeApplyResult
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ServiceState

class ProfileRemoteBatchUpdateRunnerTest {
    @Test
    fun `returns no remote profiles without starting updates`() = runBlocking {
        var updates = 0
        val runner = runner(
            profiles = listOf(profile("local", isRemote = false)),
            updateProfile = { _, _ -> updates++ },
        )

        assertSame(ProfileRemoteBatchUpdateResult.NoRemoteProfiles, runner.run())
        assertEquals(0, updates)
    }

    @Test
    fun `updates only remote profiles and applies active profile`() = runBlocking {
        val updated = mutableListOf<String>()
        var restarts = 0
        val runner = runner(
            profiles = listOf(
                profile("local", isRemote = false),
                profile("active", isRemote = true),
                profile("other", isRemote = true),
            ),
            activeProfileId = { "active" },
            serviceState = MutableStateFlow(ServiceState.RUNNING),
            updateProfile = { id, afterCommit ->
                updated += id
                afterCommit()
            },
            restartVpn = { restarts++ },
        )

        val result = runner.run() as ProfileRemoteBatchUpdateResult.Completed

        assertEquals(listOf("active", "other"), updated)
        assertEquals(2, result.batch.succeeded)
        assertEquals(0, result.batch.failed)
        assertSame(ProfileUpdateRuntimeApplyResult.Reloaded, result.batch.runtimeApply)
        assertEquals(1, restarts)
    }

    private fun runner(
        profiles: List<ProfileSummary>,
        updateProfile: suspend (String, suspend () -> Unit) -> Unit = { _, afterCommit -> afterCommit() },
        activeProfileId: () -> String? = { null },
        serviceState: MutableStateFlow<ServiceState> = MutableStateFlow(ServiceState.STOPPED),
        restartVpn: suspend () -> Unit = {},
    ) = ProfileRemoteBatchUpdateRunner(
        listProfiles = { profiles },
        updateProfile = updateProfile,
        activeProfileId = activeProfileId,
        serviceState = serviceState,
        restartVpn = restartVpn,
    )

    private fun profile(id: String, isRemote: Boolean) = ProfileSummary(
        id = id,
        name = id,
        filePath = "/tmp/$id.yaml",
        type = if (isRemote) ProfileType.REMOTE else ProfileType.LOCAL,
        isActive = false,
        isRemote = isRemote,
        lastUpdated = null,
        fileSize = 0L,
        url = if (isRemote) "https://example.com/$id.yaml" else null,
    )
}
