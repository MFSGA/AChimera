package rs.chimera.android.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import rs.chimera.android.backend.model.RemoteProfileSettings
import rs.chimera.android.util.PrivacySafeLog
import rs.chimera.android.util.runCatchingRecoverable

internal suspend fun completeProfileMetadataCommit(block: suspend () -> Unit) =
    withContext(NonCancellable) { block() }

internal fun requestImmediateProfileRefreshBestEffort(
    request: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    try {
        request()
    } catch (error: Exception) {
        onFailure(error)
    }
}

internal fun writeProfileRefreshStateBestEffort(
    write: () -> Unit,
    clearStaleState: () -> Boolean,
    onFailure: (Throwable) -> Unit,
) {
    try {
        write()
    } catch (firstError: Exception) {
        onFailure(firstError)
        try {
            write()
        } catch (retryError: Exception) {
            onFailure(retryError)
            clearProfileAutoUpdateStateBestEffort(
                clear = clearStaleState,
                onFailure = onFailure,
            )
        }
    }
}

internal fun clearProfileAutoUpdateStateBestEffort(
    clear: () -> Boolean,
    onFailure: (Throwable) -> Unit,
) {
    try {
        if (!clear()) {
            onFailure(IllegalStateException("Failed to clear profile auto-update state"))
        }
    } catch (error: Exception) {
        onFailure(error)
    }
}

internal fun refreshCommittedProfileMetadataBestEffort(
    refresh: () -> Unit,
    onFailure: (Throwable) -> Unit,
) {
    runCatchingRecoverable(refresh).onFailure(onFailure)
}

internal class ProfileMetadataOperations(
    private val profileCatalogStore: ProfileCatalogStore,
    private val profileCatalogCoordinator: ProfileCatalogCoordinator,
    private val profileUpdateCoordinator: ProfileUpdateCoordinator,
    private val profileAutoUpdateStateStore: ProfileAutoUpdateStateStore,
    private val refreshActiveProfile: () -> Unit,
    private val synchronizeProfileAutoUpdateSchedule: suspend (() -> Unit, () -> Unit) -> ProfileAutoUpdateScheduleSyncResult,
    private val requestImmediateProfileRefresh: () -> Unit,
) {
    suspend fun renameProfile(id: String, newName: String) = withContext(Dispatchers.IO) {
        profileUpdateCoordinator.withLock(id) {
            val normalizedName = newName.trim()
            require(normalizedName.isNotEmpty()) { "Profile name is empty" }
            completeProfileMetadataCommit {
                profileCatalogCoordinator.withLock {
                    val document = profileCatalogStore.readDocument()
                    val updatedProfiles = ProfileCatalogPolicy.rename(
                        document.entries,
                        id,
                        normalizedName,
                    ) ?: throw IllegalArgumentException("Profile not found")
                    profileCatalogStore.commitCatalog(
                        catalog = profileCatalogStore.render(document, updatedProfiles),
                        activePath = ProfileCatalogPolicy.activePath(updatedProfiles),
                    )
                    refreshCommittedProfileMetadataBestEffort(
                        refresh = refreshActiveProfile,
                        onFailure = { error ->
                            PrivacySafeLog.warning(
                                TAG,
                                "Failed to refresh active profile after rename",
                                error,
                                debugDetail = "profileId=$id",
                            )
                        },
                    )
                }
            }
        }
    }

    suspend fun updateRemoteProfileSettings(id: String, settings: RemoteProfileSettings) =
        withContext(Dispatchers.IO) {
            profileUpdateCoordinator.withLock(id) {
                val normalized = ProfileRemotePolicy.normalizeSettings(settings)
                val current = profileCatalogStore.readRemoteProfile(id)
                val invalidatesAutoUpdateState =
                    ProfileRemotePolicy.invalidatesAutoUpdateState(current, normalized)
                completeProfileMetadataCommit {
                    profileCatalogStore.updateRemoteProfileSettings(
                        id,
                        normalized,
                        resetLastUpdated = ProfileRemotePolicy.shouldResetLastUpdated(current, normalized),
                        requireBoundAutoUpdateState = invalidatesAutoUpdateState,
                    )
                    if (invalidatesAutoUpdateState) {
                        if (normalized.autoUpdate) {
                            writeProfileRefreshStateBestEffort(
                                write = {
                                    profileAutoUpdateStateStore.write(
                                        id,
                                        ProfileAutoUpdatePolicy.refreshNowState(System.currentTimeMillis()).copy(
                                            sourceFingerprint = ProfileAutoUpdatePolicy.sourceFingerprint(
                                                autoUpdate = normalized.autoUpdate,
                                                url = normalized.url,
                                                userAgent = normalized.userAgent,
                                                proxyUrl = normalized.proxyUrl,
                                            ),
                                            profileRevision = 0L,
                                        ),
                                    )
                                },
                                clearStaleState = { profileAutoUpdateStateStore.clear(id) },
                                onFailure = {
                                    PrivacySafeLog.warningDetail(
                                        TAG,
                                        "Failed to persist edited profile auto-update state",
                                        "profileId=$id",
                                    )
                                },
                            )
                        } else {
                            clearProfileAutoUpdateStateBestEffort(
                                clear = { profileAutoUpdateStateStore.clear(id) },
                                onFailure = {
                                    PrivacySafeLog.warningDetail(
                                        TAG,
                                        "Failed to clear edited auto-update state",
                                        "profileId=$id",
                                    )
                                },
                            )
                        }
                    }
                    refreshCommittedProfileMetadataBestEffort(
                        refresh = refreshActiveProfile,
                        onFailure = { error ->
                            PrivacySafeLog.warning(
                                TAG,
                                "Failed to refresh active profile after settings update",
                                error,
                                debugDetail = "profileId=$id",
                            )
                        },
                    )
                    val scheduleActions = profileMetadataScheduleActions(
                        autoUpdateEnabled = normalized.autoUpdate,
                        invalidatesAutoUpdateState = invalidatesAutoUpdateState,
                    )
                    val requestImmediateRefresh = {
                        requestImmediateProfileRefreshBestEffort(
                            request = requestImmediateProfileRefresh,
                            onFailure = {
                                PrivacySafeLog.warningDetail(
                                    TAG,
                                    "Failed to request immediate edited profile refresh",
                                    "profileId=$id",
                                )
                            },
                        )
                    }
                    synchronizeProfileAutoUpdateSchedule(
                        {
                            if (scheduleActions.requestImmediateAfterRefresh) {
                                requestImmediateRefresh()
                            }
                        },
                        {
                            if (scheduleActions.requestImmediateAfterFailure) {
                                requestImmediateRefresh()
                            }
                        },
                    )
                }
            }
        }

    private companion object {
        const val TAG = "ChimeraBackend"
    }
}
