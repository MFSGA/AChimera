package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

class ProfileAutoUpdateScheduleSyncTest {
    @Test
    fun successfulLoadRefreshesSchedule() = runBlocking {
        val profiles = listOf(remoteProfile())
        var refreshed = emptyList<ProfileSummary>()

        ProfileAutoUpdateScheduleSync.run(
            loadProfiles = { profiles },
            refreshSchedule = { refreshed = it },
            onFailure = { error("unexpected failure: $it") },
        )

        assertEquals(profiles, refreshed)
    }

    @Test
    fun loadFailureIsReportedWithoutRefreshing() = runBlocking {
        var refreshed = false
        var failure: Throwable? = null

        ProfileAutoUpdateScheduleSync.run(
            loadProfiles = { error("catalog failed") },
            refreshSchedule = { refreshed = true },
            onFailure = { failure = it },
        )

        assertTrue(!refreshed)
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun cancellationIsPropagated() = runBlocking {
        val error = runCatching {
            ProfileAutoUpdateScheduleSync.run(
                loadProfiles = { throw CancellationException("cancelled") },
                refreshSchedule = {},
                onFailure = {},
            )
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }

    private fun remoteProfile() = ProfileSummary(
        id = "remote",
        name = "remote",
        filePath = "/profiles/remote.yaml",
        type = ProfileType.REMOTE,
        isActive = false,
        isRemote = true,
        lastUpdated = null,
        fileSize = 1,
        url = "https://example.test/profile.yaml",
        autoUpdate = true,
    )
}
