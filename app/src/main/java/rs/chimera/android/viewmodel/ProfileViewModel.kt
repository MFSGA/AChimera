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
import kotlinx.coroutines.launch
import rs.chimera.android.Global
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ChimeraBackend
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType
import uniffi.chimera_ffi.DownloadProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FileInfo(
    val name: String,
    val uri: Uri,
    val size: Long = 0,
)

class ProfileViewModel : ViewModel() {
    private val prefs = Global.application.getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE)
    private val backend: ChimeraBackend = BackendProvider.provide()

    var selectedFile by mutableStateOf<FileInfo?>(null)
        private set

    var isImporting by mutableStateOf(false)
        private set

    var isDownloading by mutableStateOf(false)
        private set

    var downloadProgress by mutableStateOf<DownloadProgress?>(null)
        private set

    private val downloadOperations = LatestOperationGate()

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

    fun loadSavedFilePath() {
        savedFilePath = prefs.getString(PROFILE_PATH_KEY, null)
        loadProfiles()
    }

    fun selectFile(
        context: Context,
        uri: Uri,
    ) {
        val fileSize = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
        } ?: 0L

        selectedFile = FileInfo(
            name = queryDisplayName(context, uri),
            uri = uri,
            size = fileSize,
        )
        statusMessage = null
    }

    fun clearSelection() {
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
        if (isImporting) return

        viewModelScope.launch {
            isImporting = true
            try {
                backend.importLocalProfile(uri, profileName)
                if (refreshFromBackendSafely()) {
                    val resolvedName = profileName?.trim()?.takeIf { it.isNotEmpty() }
                        ?: selectedFile?.name?.substringBeforeLast('.')
                        ?: "profile"
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
        if (isDownloading) return

        viewModelScope.launch {
            isDownloading = true
            downloadProgress = null
            val generation = downloadOperations.next()
            try {
                backend.importRemoteProfile(
                    RemoteProfileRequest(
                        name = profileName,
                        url = url,
                        autoUpdate = autoUpdate,
                        userAgent = userAgent,
                        proxyUrl = proxyUrl,
                    ),
                ) { progress ->
                    viewModelScope.launch {
                        if (isDownloading && downloadOperations.isCurrent(generation)) {
                            downloadProgress = progress
                        }
                    }
                }
                if (refreshFromBackendSafely()) {
                    val resolvedName = profileName?.trim()?.takeIf { it.isNotEmpty() }
                        ?: SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(Date())
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

    fun updateRemoteProfile(
        context: Context,
        profile: Profile,
    ) {
        if (isDownloading || profile.type != ProfileType.REMOTE || profile.url.isNullOrBlank()) {
            return
        }

        viewModelScope.launch {
            isDownloading = true
            downloadProgress = null
            val generation = downloadOperations.next()
            try {
                backend.updateRemoteProfile(profile.id) { progress ->
                    viewModelScope.launch {
                        if (isDownloading && downloadOperations.isCurrent(generation)) {
                            downloadProgress = progress
                        }
                    }
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
            }
        }
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

    private fun queryDisplayName(
        context: Context,
        uri: Uri,
    ): String {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: "profile"
    }

    private companion object {
        const val FILE_PREFS = "file_prefs"
        const val PROFILE_PATH_KEY = "profile_path"
    }
}
