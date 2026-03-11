package rs.chimera.android.viewmodel

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import rs.chimera.android.Global
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import uniffi.chimera_ffi.DownloadProgress
import uniffi.chimera_ffi.DownloadProgressCallback
import uniffi.chimera_ffi.downloadFileWithProgress
import uniffi.chimera_ffi.uniffiEnsureInitialized
import uniffi.chimera_ffi.verifyConfig

data class FileInfo(
    val name: String,
    val uri: Uri,
    val size: Long = 0,
)

class ProfileViewModel : ViewModel() {
    private val prefs = Global.application.getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE)

    var selectedFile by mutableStateOf<FileInfo?>(null)
        private set

    var isImporting by mutableStateOf(false)
        private set

    var isDownloading by mutableStateOf(false)
        private set

    var downloadProgress by mutableStateOf<DownloadProgress?>(null)
        private set

    var savedFilePath by mutableStateOf<String?>(null)
        private set

    var isVerifying by mutableStateOf(false)
        private set

    var verificationResult by mutableStateOf<String?>(null)
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
        selectedFile = FileInfo(
            name = queryDisplayName(context, uri),
            uri = uri,
            size = queryFileSize(context, uri),
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
                val fileName = profileName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: selectedFile?.name?.substringBeforeLast('.')
                    ?: DEFAULT_LOCAL_PROFILE_NAME

                val (file, fileSize) = withContext(Dispatchers.IO) {
                    copyProfileFileToAppDirectory(context, uri, fileName)
                }

                val profile = Profile(
                    name = file.name,
                    filePath = file.absolutePath,
                    fileSize = fileSize,
                )
                addProfile(profile)
                statusMessage = context.getString(
                    rs.chimera.android.R.string.profile_import_success,
                    file.name,
                )
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
            try {
                val resolvedName = profileName
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: generateDefaultRemoteProfileName()
                val file = withContext(Dispatchers.IO) {
                    downloadProfileToAppDirectory(
                        context = context,
                        profileName = resolvedName,
                        urlText = url,
                        userAgent = userAgent,
                        proxyUrl = proxyUrl ?: Global.proxyPort?.let { "http://127.0.0.1:$it" },
                    )
                }

                val profile = Profile(
                    name = resolvedName,
                    filePath = file.absolutePath,
                    fileSize = file.length(),
                    type = ProfileType.REMOTE,
                    url = url,
                    lastUpdated = System.currentTimeMillis(),
                    autoUpdate = autoUpdate,
                    userAgent = userAgent,
                    proxyUrl = proxyUrl,
                )
                addProfile(profile)
                statusMessage = context.getString(
                    rs.chimera.android.R.string.profile_import_success,
                    file.name,
                )
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

    fun activateProfile(profile: Profile) {
        val updatedProfiles = profiles.map {
            it.copy(isActive = it.id == profile.id)
        }
        profiles.clear()
        profiles.addAll(updatedProfiles)
        activeProfile = profiles.firstOrNull { it.isActive }
        savedFilePath = activeProfile?.filePath
        Global.updateProfilePath(activeProfile?.filePath.orEmpty())
        saveProfiles()
    }

    fun deleteProfile(profile: Profile) {
        File(profile.filePath).takeIf { it.exists() }?.delete()
        profiles.removeAll { it.id == profile.id }

        val nextActive = if (profile.isActive) profiles.firstOrNull() else activeProfile
        if (profile.isActive) {
            val updatedProfiles = profiles.mapIndexed { index, item ->
                item.copy(isActive = index == 0)
            }
            profiles.clear()
            profiles.addAll(updatedProfiles)
        }

        activeProfile = profiles.firstOrNull { it.isActive } ?: nextActive?.takeIf { profiles.any { item -> item.id == it.id } }
        savedFilePath = activeProfile?.filePath
        Global.updateProfilePath(savedFilePath.orEmpty())
        saveProfiles()
    }

    fun verifyActiveProfile(context: Context) {
        if (isVerifying) return

        val targetPath = activeProfile?.filePath ?: savedFilePath
        if (targetPath.isNullOrBlank()) {
            verificationResult = context.getString(rs.chimera.android.R.string.profile_verify_missing)
            return
        }

        isVerifying = true
        verificationResult = null

        viewModelScope.launch {
            try {
                uniffiEnsureInitialized()
                val content = withContext(Dispatchers.IO) {
                    verifyConfig(targetPath)
                }
                verificationResult = context.getString(
                    rs.chimera.android.R.string.profile_verify_success,
                    content,
                )
            } catch (error: Exception) {
                verificationResult = context.getString(
                    rs.chimera.android.R.string.profile_verify_failure,
                    error.message ?: context.getString(rs.chimera.android.R.string.profile_unknown_error),
                )
            } finally {
                isVerifying = false
            }
        }
    }

    private fun addProfile(profile: Profile) {
        val isFirstProfile = profiles.isEmpty()
        val nextProfile = if (isFirstProfile) profile.copy(isActive = true) else profile
        profiles.add(nextProfile)
        if (nextProfile.isActive) {
            activeProfile = nextProfile
            savedFilePath = nextProfile.filePath
            Global.updateProfilePath(nextProfile.filePath)
        }
        saveProfiles()
    }

    private fun loadProfiles() {
        profiles.clear()
        val profilesJson = prefs.getString(PROFILES_LIST_KEY, null) ?: return

        runCatching {
            val jsonArray = JSONArray(profilesJson)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    add(Profile(jsonArray.getJSONObject(index)))
                }
            }
        }.onSuccess { loadedProfiles ->
            profiles.addAll(loadedProfiles)
            activeProfile = profiles.firstOrNull { it.isActive }
            savedFilePath = activeProfile?.filePath ?: savedFilePath
        }.onFailure {
            statusMessage = "Failed to load saved profiles"
        }
    }

    private fun saveProfiles() {
        val jsonArray = JSONArray()
        profiles.forEach { profile ->
            jsonArray.put(profile.asJsonObject())
        }

        prefs.edit {
            putString(PROFILES_LIST_KEY, jsonArray.toString())
            putString(PROFILE_PATH_KEY, activeProfile?.filePath)
        }
    }

    private fun copyProfileFileToAppDirectory(
        context: Context,
        uri: Uri,
        profileName: String,
    ): Pair<File, Long> {
        val sourceName = queryDisplayName(context, uri)
        val extension = sourceName.substringAfterLast('.', "yaml")
        val safeFileName = sanitizeFileName("$profileName.$extension")
        val file = File(context.filesDir, safeFileName)
        val fileSize = context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open selected file")

        return file to fileSize
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
        } ?: DEFAULT_REMOTE_FILE_NAME
    }

    private fun queryFileSize(
        context: Context,
        uri: Uri,
    ): Long {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
        } ?: 0L
    }

    private fun generateDefaultRemoteProfileName(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
        return formatter.format(Date())
    }

    private suspend fun downloadProfileToAppDirectory(
        context: Context,
        profileName: String,
        urlText: String,
        userAgent: String?,
        proxyUrl: String?,
    ): File {
        val fileName = buildRemoteFileName(urlText, profileName)
        val file = File(context.filesDir, fileName)
        uniffiEnsureInitialized()

        val result = downloadFileWithProgress(
            url = urlText,
            outputPath = file.absolutePath,
            userAgent = userAgent,
            proxyUrl = proxyUrl,
            progressCallback = object : DownloadProgressCallback {
                override fun onProgress(progress: DownloadProgress) {
                    viewModelScope.launch(Dispatchers.Main) {
                        downloadProgress = progress
                    }
                }
            },
        )

        if (!result.success) {
            throw IllegalStateException(result.errorMessage ?: context.getString(rs.chimera.android.R.string.profile_unknown_error))
        }

        return file
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { DEFAULT_REMOTE_FILE_NAME }
    }

    private fun buildRemoteFileName(
        urlText: String,
        profileName: String,
    ): String {
        val remoteName = runCatching {
            java.net.URL(urlText).path.substringAfterLast('/').substringBefore('?')
        }.getOrNull().orEmpty()
        val extension = remoteName.substringAfterLast('.', "yaml")
        return sanitizeFileName("$profileName.$extension")
    }

    private companion object {
        const val FILE_PREFS = "file_prefs"
        const val PROFILE_PATH_KEY = "profile_path"
        const val PROFILES_LIST_KEY = "profiles_list"
        const val DEFAULT_LOCAL_PROFILE_NAME = "profile"
        const val DEFAULT_REMOTE_FILE_NAME = "remote-profile.yaml"
    }
}
