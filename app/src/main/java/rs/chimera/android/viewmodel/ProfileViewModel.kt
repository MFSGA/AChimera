package rs.chimera.android.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.Global
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.ProfileUpdateRuntimeApplyResult
import rs.chimera.android.backend.applyUpdatedProfileToRunningVpn
import rs.chimera.android.backend.updateRemoteProfilesBatch
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType
import rs.chimera.android.util.toUserVisibleMessage
import rs.chimera.android.backend.model.ProfileDownloadProgress

data class FileInfo(
    val name: String,
    val uri: Uri,
    val size: Long = 0,
)

class ProfileViewModel : ViewModel() {
    private val prefs = Global.application.getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE)
    private val backend: ChimeraBackend = BackendProvider.provide()
    private val profileOperationGate = ProfileOperationGate()
    private val fileSelections = LatestOperationGate()

    var selectedFile by mutableStateOf<FileInfo?>(null)
        private set

    var isImporting by mutableStateOf(false)
        private set

    var isDownloading by mutableStateOf(false)
        private set

    var isRefreshingRemoteProfiles by mutableStateOf(false)
        private set

    var isProfileOperationInProgress by mutableStateOf(false)
        private set

    var downloadProgress by mutableStateOf<ProfileDownloadProgress?>(null)
        private set

    private val downloadOperations = LatestOperationGate()
    private val downloadProgressUpdates = Channel<Pair<Long, ProfileDownloadProgress>>(Channel.CONFLATED)

    var savedFilePath by mutableStateOf<String?>(null)
        private set

    var isVerifying by mutableStateOf(false)
        private set

    var verificationResult by mutableStateOf<String?>(null)
        private set

    var verificationSucceeded by mutableStateOf<Boolean?>(null)
        private set

    var statusMessage by mutableStateOf<String?>(null)
        private set

    val profiles = mutableStateListOf<Profile>()

    var activeProfile by mutableStateOf<Profile?>(null)
        private set

    init {
        viewModelScope.launch {
            for ((generation, progress) in downloadProgressUpdates) {
                if (isDownloading && downloadOperations.isCurrent(generation)) {
                    downloadProgress = progress
                }
            }
        }
    }

    fun loadSavedFilePath() {
        savedFilePath = prefs.getString(PROFILE_PATH_KEY, null)
        loadProfiles()
    }

    fun selectFile(
        context: Context,
        uri: Uri,
    ) {
        val generation = fileSelections.next()
        viewModelScope.launch {
            try {
                val fileInfo = withContext(Dispatchers.IO) { queryFileInfo(context, uri) }
                if (fileSelections.isCurrent(generation)) {
                    selectedFile = fileInfo
                    statusMessage = null
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (fileSelections.isCurrent(generation)) {
                    selectedFile = null
                    statusMessage = error.toUserVisibleMessage(
                        context,
                        rs.chimera.android.R.string.profile_unknown_error,
                    )
                }
            }
        }
    }

    fun clearSelection() {
        fileSelections.next()
        selectedFile = null
    }

    fun clearStatusMessage() {
        statusMessage = null
    }

    fun clearVerificationResult() {
        verificationResult = null
        verificationSucceeded = null
    }

    fun saveFileToAppDirectory(
        context: Context,
        uri: Uri,
        profileName: String? = null,
    ) {
        if (!tryBeginProfileOperation()) return
        isImporting = true

        viewModelScope.launch {
            try {
                val resolvedName = backend.importLocalProfile(uri, profileName)
                if (refreshFromBackendSafely()) {
                    statusMessage = context.getString(
                        rs.chimera.android.R.string.profile_import_success,
                        resolvedName,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusMessage = context.getString(
                    rs.chimera.android.R.string.profile_import_error,
                    error.message ?: context.getString(rs.chimera.android.R.string.profile_unknown_error),
                )
            } finally {
                isImporting = false
                selectedFile = null
                endProfileOperation()
            }
        }
    }

    fun addRemoteProfile(
        context: Context,
        profileName: String?,
        url: String,
        autoUpdate: Boolean = false,
        userAgent: String? = null,
        proxyUrl: String? = null,
    ) {
        if (!tryBeginProfileOperation()) return
        isDownloading = true
        downloadProgress = null
        val generation = downloadOperations.next()

        viewModelScope.launch {
            try {
                val resolvedName = backend.importRemoteProfile(
                    RemoteProfileRequest(
                        name = profileName,
                        url = url,
                        autoUpdate = autoUpdate,
                        userAgent = userAgent,
                        proxyUrl = proxyUrl,
                    ),
                ) { progress ->
                    downloadProgressUpdates.trySend(generation to progress)
                }
                if (refreshFromBackendSafely()) {
                    statusMessage = context.getString(
                        rs.chimera.android.R.string.profile_import_success,
                        resolvedName,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusMessage = context.getString(
                    rs.chimera.android.R.string.profile_import_error,
                    error.message ?: context.getString(rs.chimera.android.R.string.profile_unknown_error),
                )
            } finally {
                isDownloading = false
                downloadProgress = null
                endProfileOperation()
            }
        }
    }

    fun activateProfile(context: Context, profile: Profile) {
        runProfileMutation(context, rs.chimera.android.R.string.profile_activate_error) {
            backend.activateProfile(profile.id)
        }
    }

    fun deleteProfile(context: Context, profile: Profile) {
        runProfileMutation(context, rs.chimera.android.R.string.profile_delete_error) {
            backend.deleteProfile(profile.id)
        }
    }

    fun renameProfile(context: Context, profile: Profile, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) return

        runProfileMutation(context, rs.chimera.android.R.string.profile_rename_error) {
            backend.renameProfile(profile.id, trimmedName)
        }
    }

    fun updateRemoteProfileSettings(
        context: Context,
        profile: Profile,
        settings: RemoteProfileSettings,
    ) {
        if (profile.type != ProfileType.REMOTE || !tryBeginProfileOperation()) return
        statusMessage = null
        viewModelScope.launch {
            try {
                backend.updateRemoteProfileSettings(profile.id, settings)
                if (refreshFromBackendSafely()) {
                    statusMessage = context.getString(rs.chimera.android.R.string.profile_settings_saved)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusMessage = context.getString(
                    rs.chimera.android.R.string.profile_settings_error,
                    error.message ?: context.getString(rs.chimera.android.R.string.profile_unknown_error),
                )
            } finally {
                endProfileOperation()
            }
        }
    }

    fun updateRemoteProfile(
        context: Context,
        profile: Profile,
    ) {
        if (profile.type != ProfileType.REMOTE || profile.url.isNullOrBlank()) return
        if (!tryBeginProfileOperation()) return
        isDownloading = true
        downloadProgress = null
        val generation = downloadOperations.next()

        viewModelScope.launch {
            try {
                backend.updateRemoteProfile(profile.id) { progress ->
                    downloadProgressUpdates.trySend(generation to progress)
                }
                if (refreshFromBackendSafely()) {
                    statusMessage = context.getString(
                        rs.chimera.android.R.string.profile_update_success,
                        profile.name,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusMessage = context.getString(
                    rs.chimera.android.R.string.profile_update_error,
                    error.message ?: context.getString(rs.chimera.android.R.string.profile_unknown_error),
                )
            } finally {
                isDownloading = false
                downloadProgress = null
                endProfileOperation()
            }
        }
    }

    fun updateAllRemoteProfiles(context: Context) {
        if (!tryBeginProfileOperation()) return
        isRefreshingRemoteProfiles = true
        statusMessage = null

        viewModelScope.launch {
            try {
                val remoteProfiles = backend.listProfiles().filter { it.isRemote }
                if (remoteProfiles.isEmpty()) {
                    statusMessage = context.getString(rs.chimera.android.R.string.profile_no_remote_profiles)
                    return@launch
                }

                val batch = updateRemoteProfilesBatch(
                    profileIds = remoteProfiles.map { it.id },
                    updateProfile = { id -> backend.updateRemoteProfile(id) },
                    activeProfileId = { backend.activeProfile.value?.id },
                    serviceState = { backend.serviceState.value },
                    restartVpn = backend::restartVpn,
                )
                if (refreshFromBackendSafely()) {
                    statusMessage = when (val runtimeApply = batch.runtimeApply) {
                        is ProfileUpdateRuntimeApplyResult.Failed -> context.resources.getQuantityString(
                            rs.chimera.android.R.plurals.profile_refresh_reload_error,
                            batch.succeeded,
                            batch.succeeded,
                            batch.failed,
                            runtimeApply.error.message
                                ?: context.getString(rs.chimera.android.R.string.profile_unknown_error),
                        )
                        else -> context.resources.getQuantityString(
                            rs.chimera.android.R.plurals.profile_refresh_result,
                            batch.succeeded,
                            batch.succeeded,
                            batch.failed,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusMessage = context.getString(
                    rs.chimera.android.R.string.profile_list_error,
                    error.message ?: context.getString(rs.chimera.android.R.string.profile_unknown_error),
                )
            } finally {
                isRefreshingRemoteProfiles = false
                endProfileOperation()
            }
        }
    }

    fun verifyActiveProfile(context: Context) {
        if (isVerifying) return

        val targetPath = activeProfile?.filePath ?: savedFilePath
        if (targetPath.isNullOrBlank()) {
            verificationSucceeded = false
            verificationResult = context.getString(rs.chimera.android.R.string.profile_verify_missing)
            return
        }

        isVerifying = true
        verificationResult = null
        verificationSucceeded = null

        viewModelScope.launch {
            try {
                val content = backend.verifyProfile(targetPath).getOrThrow()
                verificationSucceeded = true
                verificationResult = content
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                verificationSucceeded = false
                verificationResult = context.getString(
                    rs.chimera.android.R.string.profile_verify_failure,
                    error.message ?: context.getString(rs.chimera.android.R.string.profile_unknown_error),
                )
            } finally {
                isVerifying = false
            }
        }
    }

    private fun runProfileMutation(
        context: Context,
        errorMessageRes: Int,
        operation: suspend () -> Unit,
    ) {
        if (!tryBeginProfileOperation()) return
        statusMessage = null
        viewModelScope.launch {
            try {
                operation()
                refreshFromBackendSafely()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                statusMessage = context.getString(
                    errorMessageRes,
                    error.message ?: context.getString(rs.chimera.android.R.string.profile_unknown_error),
                )
            } finally {
                endProfileOperation()
            }
        }
    }

    private fun tryBeginProfileOperation(): Boolean {
        if (!profileOperationGate.tryAcquire()) return false
        isProfileOperationInProgress = true
        return true
    }

    private fun endProfileOperation() {
        isProfileOperationInProgress = false
        profileOperationGate.release()
    }

    private suspend fun refreshFromBackend() {
        val backendProfiles = backend.listProfiles()
        profiles.clear()
        profiles.addAll(backendProfiles.map { it.toProfile() })
        activeProfile = profiles.firstOrNull { it.isActive }
        savedFilePath = activeProfile?.filePath
    }

    private suspend fun refreshFromBackendSafely(): Boolean =
        try {
            refreshFromBackend()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            statusMessage = Global.application.getString(
                rs.chimera.android.R.string.profile_list_error,
                error.message ?: Global.application.getString(rs.chimera.android.R.string.profile_unknown_error),
            )
            false
        }

    private fun loadProfiles() {
        viewModelScope.launch { refreshFromBackendSafely() }
    }

    private fun queryFileInfo(
        context: Context,
        uri: Uri,
    ): FileInfo = context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        FileInfo(
            name = nameIndex.takeIf { it >= 0 }?.let(cursor::getString)?.ifBlank { "profile" } ?: "profile",
            uri = uri,
            size = sizeIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getLong) ?: 0L,
        )
    } ?: FileInfo(name = "profile", uri = uri)

    private companion object {
        const val FILE_PREFS = "file_prefs"
        const val PROFILE_PATH_KEY = "profile_path"
    }
}
