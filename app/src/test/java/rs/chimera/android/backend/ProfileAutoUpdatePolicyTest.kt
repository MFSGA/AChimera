package rs.chimera.android.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ServiceState

class ProfileAutoUpdatePolicyTest {
    @Test
    fun policySelectsOnlyConfiguredRemoteProfiles() {
        val eligible = remoteProfile(id = "eligible", autoUpdate = true)
        val profiles = listOf(
            eligible,
            remoteProfile(id = "disabled", autoUpdate = false),
            remoteProfile(id = "missing-url", autoUpdate = true, url = null),
            localProfile(),
        )

        assertEquals(listOf(eligible), ProfileAutoUpdatePolicy.eligibleProfiles(profiles))
        assertTrue(ProfileAutoUpdatePolicy.shouldSchedule(profiles))
        assertFalse(ProfileAutoUpdatePolicy.shouldSchedule(profiles - eligible))
    }

    @Test
    fun runnerUpdatesEveryEligibleProfileAndContinuesAfterFailure() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(
                remoteProfile(id = "first", autoUpdate = true),
                remoteProfile(id = "failed", autoUpdate = true),
                remoteProfile(id = "disabled", autoUpdate = false),
                remoteProfile(id = "last", autoUpdate = true),
            ),
            failedUpdates = setOf("failed"),
        )

        val result = ProfileAutoUpdateRunner(operations).run()

        assertEquals(listOf("first", "failed", "last"), operations.updatedIds)
        assertEquals(3, result.attempted)
        assertEquals(2, result.updated)
        assertEquals(1, result.failures.size)
        assertTrue(result.shouldRetry)
        assertFalse(result.restartedVpn)
    }

    @Test
    fun activeProfileUpdateRestartsRunningVpnOnce() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(
                remoteProfile(id = "active", autoUpdate = true, active = true),
                remoteProfile(id = "other", autoUpdate = true),
            ),
            initialState = ServiceState.RUNNING,
        )

        val result = ProfileAutoUpdateRunner(operations).run()

        assertEquals(1, operations.restartCount)
        assertTrue(result.restartedVpn)
        assertFalse(result.shouldRetry)
    }

    @Test
    fun failedActiveProfileDoesNotRestartVpn() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(remoteProfile(id = "active", autoUpdate = true, active = true)),
            failedUpdates = setOf("active"),
            initialState = ServiceState.RUNNING,
        )

        val result = ProfileAutoUpdateRunner(operations).run()

        assertEquals(0, operations.restartCount)
        assertFalse(result.restartedVpn)
        assertTrue(result.shouldRetry)
    }

    private class FakeOperations(
        private val profiles: List<ProfileSummary>,
        private val failedUpdates: Set<String> = emptySet(),
        initialState: ServiceState = ServiceState.STOPPED,
    ) : ProfileAutoUpdateOperations {
        override val serviceState = MutableStateFlow(initialState)
        val updatedIds = mutableListOf<String>()
        var restartCount = 0
            private set

        override suspend fun listProfiles(): List<ProfileSummary> = profiles

        override suspend fun updateRemoteProfile(id: String) {
            updatedIds += id
            check(id !in failedUpdates) { "update failed: $id" }
        }

        override suspend fun restartVpn() {
            restartCount += 1
        }
    }

    private fun remoteProfile(
        id: String,
        autoUpdate: Boolean,
        url: String? = "https://example.test/$id.yaml",
        active: Boolean = false,
    ) = ProfileSummary(
        id = id,
        name = id,
        filePath = "/profiles/$id.yaml",
        type = ProfileType.REMOTE,
        isActive = active,
        isRemote = true,
        lastUpdated = null,
        fileSize = 1,
        url = url,
        autoUpdate = autoUpdate,
    )

    private fun localProfile() = ProfileSummary(
        id = "local",
        name = "local",
        filePath = "/profiles/local.yaml",
        type = ProfileType.LOCAL,
        isActive = false,
        isRemote = false,
        lastUpdated = null,
        fileSize = 1,
    )
}
