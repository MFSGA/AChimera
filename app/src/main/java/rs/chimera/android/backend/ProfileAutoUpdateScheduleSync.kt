package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.util.runCatchingPreservingCancellation

internal data class ProfileAutoUpdateMaintenanceGeneration(
    val scheduleGeneration: Long,
    val maintenanceGeneration: Long,
)

internal class ProfileAutoUpdateScheduleGeneration {
    private var generation = 0L
    private var maintenanceGeneration = 0L

    @Synchronized
    fun next(): Long = ++generation

    @Synchronized
    fun nextMaintenance(): ProfileAutoUpdateMaintenanceGeneration =
        ProfileAutoUpdateMaintenanceGeneration(
            scheduleGeneration = generation,
            maintenanceGeneration = ++maintenanceGeneration,
        )

    fun runIfCurrent(candidate: Long, block: () -> Unit): Boolean =
        synchronized(this) {
            if (generation != candidate) return@synchronized false
            block()
            true
        }

    fun runMaintenanceIfCurrent(
        candidate: ProfileAutoUpdateMaintenanceGeneration,
        block: () -> Unit,
    ): Boolean =
        synchronized(this) {
            if (generation != candidate.scheduleGeneration) return@synchronized false
            if (maintenanceGeneration != candidate.maintenanceGeneration) return@synchronized false
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
        afterRefresh: (List<ProfileSummary>) -> Unit = {},
        afterFailure: () -> Unit = {},
        onFailure: (Throwable) -> Unit,
    ): ProfileAutoUpdateScheduleSyncResult =
        runAtGeneration(
            requestGeneration = generation.next(),
            loadProfiles = loadProfiles,
            refreshSchedule = refreshSchedule,
            afterRefresh = afterRefresh,
            afterFailure = afterFailure,
            onFailure = onFailure,
        )

    suspend fun runMaintenance(
        loadProfiles: suspend () -> List<ProfileSummary>,
        refreshSchedule: (List<ProfileSummary>) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): ProfileAutoUpdateScheduleSyncResult =
        runAtMaintenanceGeneration(
            requestGeneration = generation.nextMaintenance(),
            loadProfiles = loadProfiles,
            refreshSchedule = refreshSchedule,
            onFailure = onFailure,
        )

    private suspend fun runAtMaintenanceGeneration(
        requestGeneration: ProfileAutoUpdateMaintenanceGeneration,
        loadProfiles: suspend () -> List<ProfileSummary>,
        refreshSchedule: (List<ProfileSummary>) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): ProfileAutoUpdateScheduleSyncResult {
        val firstLoad = runCatchingPreservingCancellation { loadProfiles() }
        val loadResult = if (firstLoad.isFailure) {
            runCatchingPreservingCancellation { loadProfiles() }
        } else {
            firstLoad
        }
        return loadResult.fold(
            onSuccess = { profiles ->
                runCatchingPreservingCancellation {
                    generation.runMaintenanceIfCurrent(requestGeneration) {
                        refreshSchedule(profiles)
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
                            generation.runMaintenanceIfCurrent(requestGeneration) {
                                onFailure(error)
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
                    generation.runMaintenanceIfCurrent(requestGeneration) {
                        onFailure(error)
                    }
                ) {
                    ProfileAutoUpdateScheduleSyncResult.FAILED
                } else {
                    ProfileAutoUpdateScheduleSyncResult.STALE
                }
            },
        )
    }

    private suspend fun runAtGeneration(
        requestGeneration: Long,
        loadProfiles: suspend () -> List<ProfileSummary>,
        refreshSchedule: (List<ProfileSummary>) -> Unit,
        afterRefresh: (List<ProfileSummary>) -> Unit,
        afterFailure: () -> Unit,
        onFailure: (Throwable) -> Unit,
    ): ProfileAutoUpdateScheduleSyncResult {
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
                        afterRefresh(profiles)
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
