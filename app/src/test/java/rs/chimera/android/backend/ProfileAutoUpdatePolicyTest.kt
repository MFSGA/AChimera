package rs.chimera.android.backend

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ServiceState

class ProfileAutoUpdatePolicyTest {
    @Test
    fun policySelectsOnlyConfiguredProfilesWhoseBackoffExpired() {
        val eligible = remoteProfile(id = "eligible", autoUpdate = true)
        val deferred = remoteProfile(
            id = "deferred",
            autoUpdate = true,
            nextAutoUpdateAt = 2_000,
        )
        val profiles = listOf(
            eligible,
            deferred,
            remoteProfile(id = "disabled", autoUpdate = false),
            remoteProfile(id = "missing-url", autoUpdate = true, url = null),
            localProfile(),
        )

        assertEquals(
            listOf(eligible),
            ProfileAutoUpdatePolicy.eligibleProfiles(profiles, now = 1_000),
        )
        assertTrue(ProfileAutoUpdatePolicy.shouldSchedule(profiles))
        assertFalse(
            ProfileAutoUpdatePolicy.shouldSchedule(
                profiles.filterNot { it.id == "eligible" || it.id == "deferred" },
            ),
        )
    }

    @Test
    fun runnerPropagatesProfileListCancellation() = runBlocking {
        val operations = FakeOperations(
            profiles = emptyList(),
            cancelListProfiles = true,
        )

        val error = runCatching {
            ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
    }

    @Test
    fun runnerReportsProfileListFailureAsRetryableResult() = runBlocking {
        val operations = FakeOperations(
            profiles = emptyList(),
            failListProfiles = true,
        )

        val result = ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()

        assertEquals(0, result.attempted)
        assertEquals(0, result.updated)
        assertEquals(0, result.deferred)
        assertEquals(listOf("list:IllegalStateException"), result.failures)
        assertFalse(result.restartedVpn)
        assertTrue(result.shouldRetry)
    }

    @Test
    fun runnerPersistsSuccessAndFailureStateWithoutBlockingLaterProfiles() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(
                remoteProfile(id = "first", autoUpdate = true),
                remoteProfile(id = "failed", autoUpdate = true),
                remoteProfile(id = "disabled", autoUpdate = false),
                remoteProfile(id = "last", autoUpdate = true),
            ),
            failedUpdates = setOf("failed"),
        )

        val result = ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()

        assertEquals(listOf("first", "failed", "last"), operations.updatedIds)
        assertEquals(3, result.attempted)
        assertEquals(2, result.updated)
        assertEquals(0, result.deferred)
        assertEquals(1, result.failures.size)
        assertEquals(0, operations.states.getValue("first").failureCount)
        assertEquals(1, operations.states.getValue("failed").failureCount)
        assertTrue(result.shouldRetry)
        assertFalse(result.restartedVpn)
    }

    @Test
    fun runnerDefersProfilesUntilRetryDeadline() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(
                remoteProfile(
                    id = "deferred",
                    autoUpdate = true,
                    failures = 2,
                    nextAutoUpdateAt = 2_000,
                ),
            ),
        )

        val result = ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()

        assertTrue(operations.updatedIds.isEmpty())
        assertEquals(0, result.attempted)
        assertEquals(1, result.deferred)
        assertFalse(result.shouldRetry)
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

        val result = ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()

        assertEquals(1, operations.restartCount)
        assertTrue(result.restartedVpn)
        assertFalse(result.shouldRetry)
    }

    @Test
    fun activeProfileUpdateReportsRestartFailureAndRetries() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(remoteProfile(id = "active", autoUpdate = true, active = true)),
            initialState = ServiceState.RUNNING,
            failRestart = true,
        )

        val result = ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()

        assertEquals(1, result.updated)
        assertEquals(0, operations.states.getValue("active").failureCount)
        assertEquals(1, operations.restartCount)
        assertFalse(result.restartedVpn)
        assertEquals(listOf("restart:IllegalStateException"), result.failures)
        assertTrue(result.shouldRetry)
    }

    @Test
    fun activeProfileUpdateDoesNotRestartVpnAfterServiceStops() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(remoteProfile(id = "active", autoUpdate = true, active = true)),
            initialState = ServiceState.RUNNING,
            stopServiceAfterUpdate = true,
        )

        val result = ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()

        assertEquals(1, result.updated)
        assertEquals(0, operations.restartCount)
        assertFalse(result.restartedVpn)
        assertFalse(result.shouldRetry)
    }

    @Test
    fun runnerPropagatesUpdateCancellationWithoutWritingFailureState() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(remoteProfile(id = "active", autoUpdate = true, active = true)),
            cancelledUpdates = setOf("active"),
            initialState = ServiceState.RUNNING,
        )

        val error = runCatching {
            ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertTrue(operations.states.isEmpty())
        assertEquals(0, operations.restartCount)
    }

    @Test
    fun runnerPropagatesStateWriteCancellation() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(remoteProfile(id = "active", autoUpdate = true, active = true)),
            cancelledStateWrites = setOf("active"),
            initialState = ServiceState.RUNNING,
        )

        val error = runCatching {
            ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(0, operations.restartCount)
    }

    @Test
    fun runnerPropagatesRestartCancellation() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(remoteProfile(id = "active", autoUpdate = true, active = true)),
            initialState = ServiceState.RUNNING,
            cancelRestart = true,
        )

        val error = runCatching {
            ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(1, operations.restartCount)
    }

    @Test
    fun failedActiveProfileDoesNotRestartVpn() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(remoteProfile(id = "active", autoUpdate = true, active = true)),
            failedUpdates = setOf("active"),
            initialState = ServiceState.RUNNING,
        )

        val result = ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()

        assertEquals(0, operations.restartCount)
        assertFalse(result.restartedVpn)
        assertTrue(result.shouldRetry)
    }

    @Test
    fun successfulActiveUpdateStillRestartsVpnWhenStatePersistenceFails() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(remoteProfile(id = "active", autoUpdate = true, active = true)),
            failedStateWrites = setOf("active"),
            initialState = ServiceState.RUNNING,
        )

        val result = ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()

        assertEquals(1, result.updated)
        assertEquals(1, operations.restartCount)
        assertTrue(result.restartedVpn)
        assertEquals(listOf("state:active:IllegalStateException"), result.failures)
        assertTrue(result.shouldRetry)
    }

    @Test
    fun failedUpdateReportsStatePersistenceFailureAndContinuesLaterProfiles() = runBlocking {
        val operations = FakeOperations(
            profiles = listOf(
                remoteProfile(id = "failed", autoUpdate = true),
                remoteProfile(id = "last", autoUpdate = true),
            ),
            failedUpdates = setOf("failed"),
            failedStateWrites = setOf("failed"),
        )

        val result = ProfileAutoUpdateRunner(operations, now = { 1_000 }).run()

        assertEquals(listOf("failed", "last"), operations.updatedIds)
        assertEquals(1, result.updated)
        assertEquals(
            listOf(
                "state:failed:IllegalStateException",
                "failed:IllegalStateException",
            ),
            result.failures,
        )
        assertEquals(0, operations.states.getValue("last").failureCount)
        assertTrue(result.shouldRetry)
    }

    @Test
    fun failurePolicyUsesBoundedExponentialBackoffWithoutPersistingSecretMessage() {
        val first = ProfileAutoUpdatePolicy.failureState(
            previousFailures = 0,
            attemptedAt = 1_000,
            error = IllegalStateException(
                "https://user:password@example.test/profile?token=secret-value",
            ),
        )
        val capped = ProfileAutoUpdatePolicy.failureState(
            previousFailures = 20,
            attemptedAt = 1_000,
            error = IllegalArgumentException("failed"),
        )
        val success = ProfileAutoUpdatePolicy.successState(attemptedAt = 2_000)

        assertEquals(1, first.failureCount)
        assertEquals(
            1_000 + ProfileAutoUpdatePolicy.BASE_RETRY_DELAY_MILLIS,
            first.nextAttemptAt,
        )
        assertEquals("IllegalStateException", first.lastError)
        assertEquals(21, capped.failureCount)
        assertEquals(
            1_000 + ProfileAutoUpdatePolicy.MAX_RETRY_DELAY_MILLIS,
            capped.nextAttemptAt,
        )
        assertEquals(2_000L, success.lastAttempt)
        assertEquals(0, success.failureCount)
        assertNull(success.nextAttemptAt)
        assertNull(success.lastError)
    }

    private class FakeOperations(
        private val profiles: List<ProfileSummary>,
        private val failedUpdates: Set<String> = emptySet(),
        private val failedStateWrites: Set<String> = emptySet(),
        private val cancelledUpdates: Set<String> = emptySet(),
        private val cancelledStateWrites: Set<String> = emptySet(),
        initialState: ServiceState = ServiceState.STOPPED,
        private val failRestart: Boolean = false,
        private val failListProfiles: Boolean = false,
        private val cancelListProfiles: Boolean = false,
        private val stopServiceAfterUpdate: Boolean = false,
        private val cancelRestart: Boolean = false,
    ) : ProfileAutoUpdateOperations {
        override val serviceState = MutableStateFlow(initialState)
        val updatedIds = mutableListOf<String>()
        val states = mutableMapOf<String, ProfileAutoUpdateState>()
        var restartCount = 0
            private set

        override suspend fun listProfiles(): List<ProfileSummary> {
            if (cancelListProfiles) throw CancellationException("list cancelled")
            check(!failListProfiles) { "list failed" }
            return profiles
        }

        override suspend fun updateRemoteProfile(id: String) {
            updatedIds += id
            if (id in cancelledUpdates) throw CancellationException("update cancelled: $id")
            check(id !in failedUpdates) { "update failed: $id" }
            if (stopServiceAfterUpdate) serviceState.value = ServiceState.STOPPED
        }

        override suspend fun recordAutoUpdateState(
            id: String,
            state: ProfileAutoUpdateState,
        ) {
            if (id in cancelledStateWrites) throw CancellationException("state write cancelled: $id")
            check(id !in failedStateWrites) { "state write failed: $id" }
            states[id] = state
        }

        override suspend fun restartVpn() {
            restartCount += 1
            if (cancelRestart) throw CancellationException("restart cancelled")
            check(!failRestart) { "restart failed" }
        }
    }

    private fun remoteProfile(
        id: String,
        autoUpdate: Boolean,
        url: String? = "https://example.test/$id.yaml",
        active: Boolean = false,
        failures: Int = 0,
        nextAutoUpdateAt: Long? = null,
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
        autoUpdateFailures = failures,
        nextAutoUpdateAt = nextAutoUpdateAt,
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
