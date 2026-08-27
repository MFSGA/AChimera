package rs.chimera.android.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rs.chimera.android.ffi.ChimeraFfi
import rs.chimera.android.util.PrivacySafeLog
import uniffi.chimera_ffi.DownloadProgress
import uniffi.chimera_ffi.DownloadProgressCallback
import uniffi.chimera_ffi.downloadFileWithProgress
import uniffi.chimera_ffi.verifyConfig
import java.io.File

internal fun nextRemoteProfileCommitTimestamp(previous: Long?, now: Long): Long =
    when {
        previous == null || previous < now -> now
        previous < Long.MAX_VALUE -> previous + 1
        else -> Long.MAX_VALUE
    }

internal class ProfileRemoteUpdateOperations(
    private val profileCatalogStore: ProfileCatalogStore,
    private val profileStagingStore: ProfileStagingStore,
    private val profileUpdateCoordinator: ProfileUpdateCoordinator,
    private val profileAutoUpdateStateStore: ProfileAutoUpdateStateStore,
    private val proxyPort: () -> UShort?,
    private val updateRuntimePath: (String?) -> Unit,
    private val refreshActiveProfile: () -> Unit,
) {
    suspend fun updateRemoteProfile(
        id: String,
        onProgress: (DownloadProgress) -> Unit,
        afterCommit: suspend (committedAt: Long) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        profileUpdateCoordinator.withLock(id) {
            val targetProfile = profileCatalogStore.readRemoteProfile(id)
            require(targetProfile.type == "REMOTE") { "Profile is not remote" }
            val url = targetProfile.url
                ?: throw IllegalStateException("Remote profile URL is missing")
            ProfileRemotePolicy.requireValidUrl(url)

            val userAgent = targetProfile.userAgent
            val proxyUrl = targetProfile.proxyUrl
                ?: proxyPort()?.let { "http://127.0.0.1:$it" }
            val outputFile = File(targetProfile.filePath)

            var committedAt: Long? = null
            val updatedActiveProfile = ProfileUpdateTransactionPolicy.run(
                destinationFile = outputFile,
                update = {
                    val tempFile = ProfileDownloadRecoveryPolicy.createStage(outputFile)
                    tempFile.delete()
                    try {
                        ChimeraFfi.ensureInitialized()
                        val result = downloadFileWithProgress(
                            url = url,
                            outputPath = tempFile.absolutePath,
                            userAgent = userAgent,
                            proxyUrl = proxyUrl,
                            progressCallback = object : DownloadProgressCallback {
                                override fun onProgress(progress: DownloadProgress) {
                                    onProgress(progress)
                                }
                            },
                        )
                        if (!result.success) {
                            throw IllegalStateException(result.errorMessage ?: "Unknown download error")
                        }
                        ProfileImportPolicy.requireUsableDownloadedProfile(tempFile)
                        verifyConfig(tempFile.absolutePath)
                        tempFile
                    } catch (error: Throwable) {
                        tempFile.delete()
                        throw error
                    }
                },
                persistMetadata = { file, backup ->
                    val updatedAt = nextRemoteProfileCommitTimestamp(
                        previous = targetProfile.lastUpdated,
                        now = System.currentTimeMillis(),
                    )
                    profileCatalogStore.updateRemoteProfileMetadata(
                        id = id,
                        file = file,
                        backup = backup,
                        updatedAt = updatedAt,
                    ).also { committedAt = updatedAt }
                },
                beginBackupTransaction = profileStagingStore::markUpdatePending,
                clearBackupTransaction = profileStagingStore::clearUpdatePending,
            )
            val updateCommittedAt = checkNotNull(committedAt) { "Profile update commit timestamp is missing" }
            completeRemoteProfileUpdateCommit(
                clearAutoUpdateState = { profileAutoUpdateStateStore.clear(id) },
                synchronizeRuntimePath = {
                    if (updatedActiveProfile) updateRuntimePath(outputFile.absolutePath)
                },
                refreshActiveProfile = refreshActiveProfile,
                afterCommit = { afterCommit(updateCommittedAt) },
            ) { error ->
                PrivacySafeLog.warning(TAG, "Failed remote profile update post-commit maintenance", error)
            }
        }
    }

    private companion object {
        const val TAG = "ChimeraBackend"
    }
}
