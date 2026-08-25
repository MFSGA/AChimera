package rs.chimera.android.backend

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
    fun transientLoadFailureIsRetriedBeforeScheduling() = runBlocking {
        val profiles = listOf(remoteProfile())
        var attempts = 0
        var refreshed = emptyList<ProfileSummary>()

        ProfileAutoUpdateScheduleSync.run(
            loadProfiles = {
                attempts += 1
                if (attempts == 1) error("transient catalog failure")
                profiles
            },
            refreshSchedule = { refreshed = it },
            onFailure = { error("unexpected failure: $it") },
        )

        assertEquals(2, attempts)
        assertEquals(profiles, refreshed)
    }

    @Test
    fun scheduleFailureIsReportedAsFailed() = runBlocking {
        var failure: Throwable? = null

        val result = ProfileAutoUpdateScheduleSync.run(
            loadProfiles = { listOf(remoteProfile()) },
            refreshSchedule = { error("schedule failed") },
            onFailure = { failure = it },
        )

        assertEquals(ProfileAutoUpdateScheduleSyncResult.FAILED, result)
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun scheduleFailureDoesNotRunAfterRefreshAction() = runBlocking {
        var afterRefreshRan = false

        val result = ProfileAutoUpdateScheduleSync.run(
            loadProfiles = { listOf(remoteProfile()) },
            refreshSchedule = { error("schedule failed") },
            afterRefresh = { afterRefreshRan = true },
            onFailure = {},
        )

        assertEquals(ProfileAutoUpdateScheduleSyncResult.FAILED, result)
        assertTrue(!afterRefreshRan)
    }

    @Test
    fun loadFailureIsReportedWithoutRefreshing() = runBlocking {
        var refreshed = false
        var failure: Throwable? = null

        val result = ProfileAutoUpdateScheduleSync.run(
            loadProfiles = { error("catalog failed") },
            refreshSchedule = { refreshed = true },
            onFailure = { failure = it },
        )

        assertEquals(ProfileAutoUpdateScheduleSyncResult.FAILED, result)
        assertTrue(!refreshed)
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun currentFailureRunsRecoveryInsideGenerationBoundary() = runBlocking {
        var recovered = false

        val result = ProfileAutoUpdateScheduleSync.run(
            loadProfiles = { error("catalog failed") },
            refreshSchedule = {},
            afterFailure = { recovered = true },
            onFailure = {},
        )

        assertEquals(ProfileAutoUpdateScheduleSyncResult.FAILED, result)
        assertTrue(recovered)
    }

    @Test
    fun staleFailureDoesNotRunRecovery() = runBlocking {
        val secondLoadStarted = CompletableDeferred<Unit>()
        val releaseSecondLoad = CompletableDeferred<Unit>()
        var attempts = 0
        var staleRecoveryRan = false

        val first = async {
            ProfileAutoUpdateScheduleSync.run(
                loadProfiles = {
                    attempts += 1
                    if (attempts == 1) error("transient catalog failure")
                    secondLoadStarted.complete(Unit)
                    releaseSecondLoad.await()
                    error("persistent catalog failure")
                },
                refreshSchedule = {},
                afterFailure = { staleRecoveryRan = true },
                onFailure = {},
            )
        }
        secondLoadStarted.await()

        ProfileAutoUpdateScheduleSync.run(
            loadProfiles = { emptyList() },
            refreshSchedule = {},
            onFailure = { error("unexpected newer failure: $it") },
        )
        releaseSecondLoad.complete(Unit)

        assertEquals(ProfileAutoUpdateScheduleSyncResult.STALE, first.await())
        assertTrue(!staleRecoveryRan)
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

    @Test
    fun generationCheckAndScheduleApplyAreLinearized() {
        val generation = ProfileAutoUpdateScheduleGeneration()
        val oldGeneration = generation.next()
        val oldApplyStarted = CountDownLatch(1)
        val releaseOldApply = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val applied = mutableListOf<String>()

        val oldThread = Thread {
            generation.runIfCurrent(oldGeneration) {
                oldApplyStarted.countDown()
                check(releaseOldApply.await(2, TimeUnit.SECONDS))
                synchronized(applied) { applied += "old" }
            }
        }.also(Thread::start)
        assertTrue(oldApplyStarted.await(2, TimeUnit.SECONDS))

        val newThread = Thread {
            secondStarted.countDown()
            val newGeneration = generation.next()
            generation.runIfCurrent(newGeneration) {
                synchronized(applied) { applied += "new" }
            }
            secondFinished.countDown()
        }.also(Thread::start)

        assertTrue(secondStarted.await(2, TimeUnit.SECONDS))
        assertTrue(!secondFinished.await(100, TimeUnit.MILLISECONDS))
        releaseOldApply.countDown()
        oldThread.join()
        newThread.join()

        assertEquals(listOf("old", "new"), synchronized(applied) { applied.toList() })
    }

    @Test
    fun staleCatalogLoadDoesNotRunAfterRefreshAction() = runBlocking {
        val firstLoadStarted = CompletableDeferred<Unit>()
        val releaseFirstLoad = CompletableDeferred<Unit>()
        var staleAfterRefreshRan = false

        val first = async {
            ProfileAutoUpdateScheduleSync.run(
                loadProfiles = {
                    firstLoadStarted.complete(Unit)
                    releaseFirstLoad.await()
                    listOf(remoteProfile())
                },
                refreshSchedule = {},
                afterRefresh = { staleAfterRefreshRan = true },
                onFailure = { error("unexpected first failure: $it") },
            )
        }
        firstLoadStarted.await()

        ProfileAutoUpdateScheduleSync.run(
            loadProfiles = { emptyList() },
            refreshSchedule = {},
            onFailure = { error("unexpected second failure: $it") },
        )
        releaseFirstLoad.complete(Unit)
        val staleResult = first.await()

        assertEquals(ProfileAutoUpdateScheduleSyncResult.STALE, staleResult)
        assertTrue(!staleAfterRefreshRan)
    }

    @Test
    fun staleCatalogLoadDoesNotOverwriteNewerSchedule() = runBlocking {
        val firstLoadStarted = CompletableDeferred<Unit>()
        val releaseFirstLoad = CompletableDeferred<Unit>()
        val refreshed = mutableListOf<List<ProfileSummary>>()
        val oldProfiles = emptyList<ProfileSummary>()
        val newProfiles = listOf(remoteProfile())

        val first = async {
            ProfileAutoUpdateScheduleSync.run(
                loadProfiles = {
                    firstLoadStarted.complete(Unit)
                    releaseFirstLoad.await()
                    oldProfiles
                },
                refreshSchedule = { refreshed += it },
                onFailure = { error("unexpected first failure: $it") },
            )
        }
        firstLoadStarted.await()

        ProfileAutoUpdateScheduleSync.run(
            loadProfiles = { newProfiles },
            refreshSchedule = { refreshed += it },
            onFailure = { error("unexpected second failure: $it") },
        )
        releaseFirstLoad.complete(Unit)
        first.await()

        assertEquals(listOf(newProfiles), refreshed)
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
