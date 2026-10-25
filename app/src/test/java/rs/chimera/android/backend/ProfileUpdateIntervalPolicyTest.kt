package rs.chimera.android.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import rs.chimera.android.backend.model.ProfileUpdateInterval
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.backend.model.RemoteProfileSettings
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.ui.metacubex.activity.ProfileMutationSnapshot
import rs.chimera.android.viewmodel.matchesMutationSnapshot
import rs.chimera.android.viewmodel.toProfile
import java.security.MessageDigest

class ProfileUpdateIntervalPolicyTest {
    private val halfHour = 30L * ProfileUpdateInterval.MINUTE_MILLIS
    private val profile = ProfileSummary(
        "fixture", "fixture", "/fixture.yaml", type = ProfileType.REMOTE,
        isActive = false, isRemote = true, lastUpdated = 1_000L, fileSize = 1L,
        url = "https://example.com/profile.yaml", autoUpdate = true,
    )
    private val current = RemoteProfileCatalogEntry(
        "REMOTE", profile.url, true, null, null, profile.filePath, profile.lastUpdated,
    )
    private val settings = RemoteProfileSettings("fixture", profile.url!!, true, autoUpdateIntervalMillis = halfHour)

    @Test
    fun minutesAcceptEmptyOrAtLeastFifteenAndRejectOverflow() {
        assertEquals(0L, ProfileUpdateInterval.parseMinutes(""))
        assertEquals(halfHour, ProfileUpdateInterval.parseMinutes("30"))
        assertEquals(ProfileUpdateInterval.MIN_MILLIS, ProfileUpdateInterval.parseMinutes("15"))
        listOf("0", "14", "-1", "15.5", " 15 ", "text", Long.MAX_VALUE.toString()).forEach {
            assertNull(ProfileUpdateInterval.parseMinutes(it))
        }
        val maximum = Long.MAX_VALUE / ProfileUpdateInterval.MINUTE_MILLIS
        assertEquals(maximum * ProfileUpdateInterval.MINUTE_MILLIS, ProfileUpdateInterval.parseMinutes(maximum.toString()))
        assertNull(ProfileUpdateInterval.parseMinutes((maximum + 1).toString()))
    }

    @Test
    fun backendRejectsInvalidDurationsForImportAndEdit() {
        listOf(0L, -1L, ProfileUpdateInterval.MIN_MILLIS - 1, halfHour + 1).forEach { invalid ->
            assertTrue(runCatching {
                ProfileRemotePolicy.normalizeRequest(RemoteProfileRequest(null, profile.url!!, autoUpdateIntervalMillis = invalid))
            }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching {
                ProfileRemotePolicy.normalizeSettings(settings.copy(autoUpdateIntervalMillis = invalid))
            }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun deadlineSaturatesInsteadOfOverflowing() {
        assertEquals(Long.MAX_VALUE, ProfileUpdateInterval.dueAt(Long.MAX_VALUE - 1, halfHour))
    }

    @Test
    fun eachProfileUsesItsOwnDeadlineAndExplicitRetryStillWins() {
        val frequent = profile.copy(id = "frequent", autoUpdateIntervalMillis = halfHour)
        assertEquals(listOf(frequent), ProfileAutoUpdatePolicy.eligibleProfiles(listOf(profile, frequent), 1_000L + halfHour))
        assertTrue(ProfileAutoUpdatePolicy.eligibleProfiles(listOf(frequent), 999L + halfHour).isEmpty())
        val retry = frequent.copy(nextAutoUpdateAt = 100L)
        assertEquals(listOf(retry), ProfileAutoUpdatePolicy.eligibleProfiles(listOf(retry), 100L))
        assertEquals(2_000L + halfHour, ProfileAutoUpdatePolicy.successState(2_000L, halfHour).nextAttemptAt)
    }

    @Test
    fun sharedJobUsesShortestEnabledIntervalAndRetainsDailyUpperCadence() {
        assertNull(ProfileAutoUpdatePolicy.scheduleIntervalMillis(listOf(profile.copy(autoUpdate = false))))
        assertEquals(halfHour, ProfileAutoUpdatePolicy.scheduleIntervalMillis(listOf(
            profile, profile.copy(autoUpdateIntervalMillis = halfHour),
            profile.copy(autoUpdate = false, autoUpdateIntervalMillis = ProfileUpdateInterval.MIN_MILLIS),
        )))
        assertEquals(ProfileUpdateInterval.DEFAULT_MILLIS, ProfileAutoUpdatePolicy.scheduleIntervalMillis(listOf(
            profile.copy(autoUpdateIntervalMillis = 2 * ProfileUpdateInterval.DEFAULT_MILLIS),
        )))
    }

    @Test
    fun downloadSourceFingerprintStaysCompatibleWhileIntervalHasItsOwnIdentity() {
        val legacy = listOf("true", profile.url, "", "").joinToString("\u0000")
        val expected = MessageDigest.getInstance("SHA-256").digest(legacy.toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(expected, ProfileAutoUpdatePolicy.sourceFingerprint(profile))
        assertEquals(expected, ProfileAutoUpdatePolicy.sourceFingerprint(profile.copy(autoUpdateIntervalMillis = halfHour)))
    }

    @Test
    fun changingIntervalRejectsStaleStateAndMutationSnapshots() {
        val changed = profile.copy(autoUpdateIntervalMillis = halfHour)
        val oldState = ProfileAutoUpdatePolicy.bindStateToSource(profile, ProfileAutoUpdatePolicy.successState(2_000L))
        assertTrue(ProfileAutoUpdatePolicy.stateMatchesSource(changed, oldState))
        assertFalse(ProfileAutoUpdateStateCommitPolicy.canRecord(current.copy(autoUpdateIntervalMillis = halfHour), oldState))
        assertFalse(ProfileAutoUpdateLegacyBindingPolicy.snapshotStillMatchesCurrentCatalog(profile, current.copy(autoUpdateIntervalMillis = halfHour)))
        assertFalse(profile.hasSameAutoUpdateSource(changed))
        assertFalse(ProfileMutationSnapshot.from(profile).matches(changed))
        assertFalse(profile.toProfile().matchesMutationSnapshot(changed))
        assertEquals(halfHour, changed.toProfile().autoUpdateIntervalMillis)
    }

    @Test
    fun intervalOnlyEditPreservesRevisionAndPendingRuntimeApply() {
        val previous = ProfileAutoUpdatePolicy.bindStateToSource(
            profile, ProfileAutoUpdatePolicy.successState(2_000L).copy(runtimeApplyPending = true),
        )
        assertFalse(ProfileRemotePolicy.invalidatesAutoUpdateState(current, settings))
        assertFalse(ProfileRemotePolicy.shouldResetLastUpdated(current, settings))
        val edited = ProfileAutoUpdatePolicy.stateForInterval(profile.copy(autoUpdateIntervalMillis = halfHour), previous)
        assertEquals(profile.lastUpdated, edited.profileRevision)
        assertEquals(1_000L + halfHour, edited.nextAttemptAt)
        assertEquals(previous.lastAttempt, edited.lastAttempt)
        assertTrue(edited.runtimeApplyPending)
        assertTrue(ProfileAutoUpdateStateCommitPolicy.canRecord(current.copy(autoUpdateIntervalMillis = halfHour), edited))
    }

    @Test
    fun intervalOnlyEditPreservesFailureBackoffInsteadOfRedownloading() {
        val previous = ProfileAutoUpdatePolicy.bindStateToSource(
            profile, ProfileAutoUpdatePolicy.failureState(2, 2_000L, IllegalStateException()),
        )
        val edited = ProfileAutoUpdatePolicy.stateForInterval(profile.copy(autoUpdateIntervalMillis = halfHour), previous)
        assertEquals(previous.nextAttemptAt, edited.nextAttemptAt)
        assertEquals(previous.failureCount, edited.failureCount)
        assertEquals(previous.lastError, edited.lastError)
    }

    @Test
    fun legacyBindingDoesNotMislabelAnOldDailyDeadlineAsTheNewInterval() {
        val previous = ProfileAutoUpdatePolicy.bindStateToSource(
            profile, ProfileAutoUpdatePolicy.successState(2_000L).copy(runtimeApplyPending = true),
        )
        val custom = profile.copy(autoUpdateIntervalMillis = halfHour)
        val legacy = ProfileAutoUpdatePolicy.bindLegacyStateToCurrentProfile(
            custom, previous.copy(sourceFingerprint = null, profileRevision = null),
        )
        assertEquals(ProfileUpdateInterval.DEFAULT_MILLIS, legacy.intervalMillis)
        assertEquals(1_000L + halfHour, ProfileAutoUpdatePolicy.stateForInterval(custom, legacy).nextAttemptAt)
    }

    @Test
    fun runnerDoesNotRecordTheStartingIntervalAfterAConcurrentEdit() = runBlocking {
        var currentProfile = profile.copy(lastUpdated = null)
        var writes = 0
        val operations = object : ProfileAutoUpdateOperations {
            override val serviceState = MutableStateFlow(ServiceState.STOPPED)
            override suspend fun listProfiles() = listOf(currentProfile)
            override suspend fun updateRemoteProfile(id: String) {
                currentProfile = currentProfile.copy(autoUpdateIntervalMillis = halfHour, lastUpdated = 6_000L)
            }
            override suspend fun recordAutoUpdateState(id: String, state: ProfileAutoUpdateState) {
                writes++
            }
            override suspend fun restartVpn() = error("VPN must remain stopped")
        }
        assertEquals(1, ProfileAutoUpdateRunner(operations) { 5_000L }.run().updated)
        assertEquals(0, writes)
    }

    @Test
    fun runnerRecordsCustomIntervalAfterSuccessfulDownload() = runBlocking {
        var recorded: ProfileAutoUpdateState? = null
        val operations = object : ProfileAutoUpdateOperations {
            override val serviceState = MutableStateFlow(ServiceState.STOPPED)
            override suspend fun listProfiles() = listOf(profile.copy(lastUpdated = null, autoUpdateIntervalMillis = halfHour))
            override suspend fun updateRemoteProfile(id: String) = Unit
            override suspend fun recordAutoUpdateState(id: String, state: ProfileAutoUpdateState) {
                recorded = state
            }
            override suspend fun restartVpn() = error("VPN must remain stopped")
        }
        assertEquals(1, ProfileAutoUpdateRunner(operations) { 5_000L }.run().updated)
        assertEquals(5_000L + halfHour, recorded?.nextAttemptAt)
    }
}
