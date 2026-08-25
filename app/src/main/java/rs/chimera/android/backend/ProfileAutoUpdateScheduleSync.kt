package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.util.runCatchingPreservingCancellation

internal class ProfileAutoUpdateScheduleGeneration {
    private var generation = 0L

    @Synchronized
    fun next(): Long = ++generation

    fun runIfCurrent(candidate: Long, block: () -> Unit): Boolean =
        synchronized(this) {
            if (generation != candidate) return@synchronized false
            block()
            true
        }
}

internal enum class ProfileAutoUpdateScheduleSyncResult {
    APPLIED,
    FAILED,
    STALE,
}

internal object ProfileAutoUpdateScheduleSync {
    private val generation = ProfileAutoUpdateScheduleGeneration()

    suspend fun run(
        loadProfiles: suspend () -> List<ProfileSummary>,
        refreshSchedule: (List<ProfileSummary>) -> Unit,
        afterRefresh: () -> Unit = {},
        afterFailure: () -> Unit = {},
        onFailure: (Throwable) -> Unit,
    ): ProfileAutoUpdateScheduleSyncResult {
        val requestGeneration = generation.next()
        val firstLoad = runCatchingPreservingCancellation { loadProfiles() }
        val loadResult = if (firstLoad.isFailure) {
            runCatchingPreservingCancellation { loadProfiles() }
        } else {
            firstLoad
        }
        return loadResult.fold(
            onSuccess = { profiles ->
                runCatchingPreservingCancellation {
                    generation.runIfCurrent(requestGeneration) {
                        refreshSchedule(profiles)
                        afterRefresh()
                    }
                }.fold(
                    onSuccess = { applied ->
                        if (applied) {
                            ProfileAutoUpdateScheduleSyncResult.APPLIED
                        } else {
                            ProfileAutoUpdateScheduleSyncResult.STALE
                        }
                    },
                    onFailure = { error ->
                        if (
                            generation.runIfCurrent(requestGeneration) {
                                onFailure(error)
                                afterFailure()
                            }
                        ) {
                            ProfileAutoUpdateScheduleSyncResult.FAILED
                        } else {
                            ProfileAutoUpdateScheduleSyncResult.STALE
                        }
                    },
                )
            },
            onFailure = { error ->
                if (
                    generation.runIfCurrent(requestGeneration) {
                        onFailure(error)
                        afterFailure()
                    }
                ) {
                    ProfileAutoUpdateScheduleSyncResult.FAILED
                } else {
                    ProfileAutoUpdateScheduleSyncResult.STALE
                }
            },
        )
    }
}
