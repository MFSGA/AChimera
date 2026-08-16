package rs.chimera.android.backend

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.ffi.ChimeraFfi
import rs.chimera.android.model.ProfileType
import uniffi.chimera_ffi.DownloadProgress
import uniffi.chimera_ffi.DownloadProgressCallback
import uniffi.chimera_ffi.downloadFileWithProgress
import uniffi.chimera_ffi.verifyConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

internal class ProfileImportOperations(
    private val context: Context,
    private val profileCatalogStore: ProfileCatalogStore,
    private val profileStagingStore: ProfileStagingStore,
    private val proxyPort: () -> UShort?,
) {
    suspend fun importLocalProfile(uri: Uri, name: String?): Boolean {
        val fileName = queryDisplayName(uri)
        val safeName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: fileName.substringBeforeLast('.')
        val id = UUID.randomUUID().toString()
        val destinationFile = File(
            context.filesDir,
            ProfileRemotePolicy.storageFileName(id, fileName),
        )
        val stagedFile = ProfileImportRecoveryPolicy.createStage(destinationFile)

        withContext(Dispatchers.IO) {
            ProfileFilePolicy.writeOrRollback(stagedFile) { target ->
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to open selected profile")
                input.use {
                    target.outputStream().use { output ->
                        ProfileImportPolicy.copyWithLimit(it, output)
                    }
                }
            }
        }

        val profileJson = JSONObject()
        profileJson.put("id", id)
        profileJson.put("name", safeName)
        profileJson.put("filePath", destinationFile.absolutePath)
        profileJson.put("createdAt", System.currentTimeMillis())
        profileJson.put("isActive", false)
        profileJson.put("fileSize", stagedFile.length())
        profileJson.put("type", ProfileType.LOCAL.name)

        return ProfileImportTransactionPolicy.run(
            stagedFile = stagedFile,
            destinationFile = destinationFile,
            beginImportTransaction = profileStagingStore::markImportPending,
            persistMetadata = { file ->
                profileJson.put("filePath", file.absolutePath)
                profileJson.put("fileSize", file.length())
                profileCatalogStore.append(profileJson, pendingImport = file)
            },
            clearImportTransaction = profileStagingStore::clearImportPending,
        )
    }

    suspend fun importRemoteProfile(
        request: RemoteProfileRequest,
        onProgress: (DownloadProgress) -> Unit,
    ): Boolean {
        ProfileRemotePolicy.requireValidUrl(request.url)
        val resolvedName = request.name?.trim()?.takeIf { it.isNotEmpty() }
            ?: SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(Date())

        val id = UUID.randomUUID().toString()
        val destinationFile = File(
            context.filesDir,
            ProfileRemotePolicy.storageFileNameForUrl(id, request.url),
        )
        val stagedFile = ProfileImportRecoveryPolicy.createStage(destinationFile)
        withContext(Dispatchers.IO) {
            downloadProfileToFile(stagedFile, request, onProgress)
        }

        val profileJson = JSONObject()
        profileJson.put("id", id)
        profileJson.put("name", resolvedName)
        profileJson.put("filePath", destinationFile.absolutePath)
        profileJson.put("createdAt", System.currentTimeMillis())
        profileJson.put("isActive", false)
        profileJson.put("fileSize", stagedFile.length())
        profileJson.put("type", ProfileType.REMOTE.name)
        profileJson.put("url", request.url)
        profileJson.put("lastUpdated", System.currentTimeMillis())
        profileJson.put("autoUpdate", request.autoUpdate)
        if (request.userAgent != null) profileJson.put("userAgent", request.userAgent)
        if (request.proxyUrl != null) profileJson.put("proxyUrl", request.proxyUrl)

        return ProfileImportTransactionPolicy.run(
            stagedFile = stagedFile,
            destinationFile = destinationFile,
            beginImportTransaction = profileStagingStore::markImportPending,
            persistMetadata = { file ->
                profileJson.put("filePath", file.absolutePath)
                profileJson.put("fileSize", file.length())
                profileCatalogStore.append(profileJson, pendingImport = file)
            },
            clearImportTransaction = profileStagingStore::clearImportPending,
        )
    }

    fun verifyProfile(filePath: String): Result<String> =
        runCatching {
            ChimeraFfi.ensureInitialized()
            verifyConfig(filePath)
        }

    private fun queryDisplayName(uri: Uri): String {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: "remote-profile.yaml"
    }

    private suspend fun downloadProfileToFile(
        file: File,
        request: RemoteProfileRequest,
        onProgress: (DownloadProgress) -> Unit,
    ): File {
        return try {
            ChimeraFfi.ensureInitialized()
            val result = downloadFileWithProgress(
                url = request.url,
                outputPath = file.absolutePath,
                userAgent = request.userAgent,
                proxyUrl = request.proxyUrl ?: proxyPort()?.let { "http://127.0.0.1:$it" },
                progressCallback = object : DownloadProgressCallback {
                    override fun onProgress(progress: DownloadProgress) {
                        onProgress(progress)
                    }
                },
            )

            check(result.success) {
                result.errorMessage ?: "Unknown download error"
            }
            ProfileImportPolicy.requireUsableDownloadedProfile(file)
            verifyConfig(file.absolutePath)
            file
        } catch (error: Throwable) {
            ProfileFilePolicy.deleteAfterFailure(file, error)
            throw error
        }
    }
}
