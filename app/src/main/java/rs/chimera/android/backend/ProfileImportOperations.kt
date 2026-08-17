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
import rs.chimera.android.backend.model.ProfileDownloadProgress
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
    suspend fun importLocalProfile(uri: Uri, name: String?): Pair<Boolean, String> =
        withContext(Dispatchers.IO) {
            val fileName = queryDisplayName(uri)
            val safeName = ProfileImportPolicy.resolveLocalProfileName(name, fileName)
            val id = UUID.randomUUID().toString()
            val destinationFile = File(
                context.filesDir,
                ProfileRemotePolicy.storageFileName(id, fileName),
            )
            val stagedFile = ProfileImportRecoveryPolicy.createStage(destinationFile)

            ProfileFilePolicy.writeOrRollback(stagedFile) { target ->
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Unable to open selected profile")
                input.use {
                    target.outputStream().use { output ->
                        ProfileImportPolicy.copyWithLimit(it, output)
                    }
                }
            }
            try {
                verifyImportedProfile(stagedFile)
            } catch (error: Throwable) {
                ProfileFilePolicy.deleteAfterFailure(stagedFile, error)
                throw error
            }

            val profileJson = JSONObject()
            profileJson.put("id", id)
            profileJson.put("name", safeName)
            profileJson.put("filePath", destinationFile.absolutePath)
            profileJson.put("createdAt", System.currentTimeMillis())
            profileJson.put("isActive", false)
            profileJson.put("fileSize", stagedFile.length())
            profileJson.put("type", ProfileType.LOCAL.name)

            val isFirst = ProfileImportTransactionPolicy.run(
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
            isFirst to safeName
        }

    suspend fun importRemoteProfile(
        request: RemoteProfileRequest,
        onProgress: (ProfileDownloadProgress) -> Unit,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val normalizedRequest = ProfileRemotePolicy.normalizeRequest(request)
        val resolvedName = normalizedRequest.name
            ?: SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault()).format(Date())

        val id = UUID.randomUUID().toString()
        val destinationFile = File(
            context.filesDir,
            ProfileRemotePolicy.storageFileNameForUrl(id, normalizedRequest.url),
        )
        val stagedFile = ProfileImportRecoveryPolicy.createStage(destinationFile)
        downloadProfileToFile(stagedFile, normalizedRequest, onProgress)

        val profileJson = JSONObject()
        profileJson.put("id", id)
        profileJson.put("name", resolvedName)
        profileJson.put("filePath", destinationFile.absolutePath)
        profileJson.put("createdAt", System.currentTimeMillis())
        profileJson.put("isActive", false)
        profileJson.put("fileSize", stagedFile.length())
        profileJson.put("type", ProfileType.REMOTE.name)
        profileJson.put("url", normalizedRequest.url)
        profileJson.put("lastUpdated", System.currentTimeMillis())
        profileJson.put("autoUpdate", normalizedRequest.autoUpdate)
        if (normalizedRequest.userAgent != null) {
            profileJson.put("userAgent", normalizedRequest.userAgent)
        }
        if (normalizedRequest.proxyUrl != null) {
            profileJson.put("proxyUrl", normalizedRequest.proxyUrl)
        }

        val isFirst = ProfileImportTransactionPolicy.run(
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
        isFirst to resolvedName
    }

    suspend fun verifyProfile(filePath: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                ChimeraFfi.ensureInitialized()
                verifyConfig(filePath)
            }
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

    private fun verifyImportedProfile(file: File) {
        ChimeraFfi.ensureInitialized()
        verifyConfig(file.absolutePath)
    }

    private suspend fun downloadProfileToFile(
        file: File,
        request: RemoteProfileRequest,
        onProgress: (ProfileDownloadProgress) -> Unit,
    ): File {
        return try {
            ChimeraFfi.ensureInitialized()
            val result = downloadFileWithProgress(
                url = request.url,
                outputPath = file.absolutePath,
                userAgent = request.userAgent,
                proxyUrl = request.proxyUrl ?: proxyPort()?.let { "http://127.0.0.1:$it" },
                progressCallback = object : DownloadProgressCallback {
                    override fun onProgress(progress: uniffi.chimera_ffi.DownloadProgress) {
                        onProgress(ProfileDownloadProgress(progress.downloaded, progress.total))
                    }
                },
            )

            check(result.success) {
                result.errorMessage ?: "Unknown download error"
            }
            ProfileImportPolicy.requireUsableDownloadedProfile(file)
            verifyImportedProfile(file)
            file
        } catch (error: Throwable) {
            ProfileFilePolicy.deleteAfterFailure(file, error)
            throw error
        }
    }
}
