package rs.chimera.android.backend

import org.json.JSONObject
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType
import java.io.File

internal class ProfileCatalogReader(
    private val catalogStore: ProfileCatalogStore,
    private val autoUpdateStateStore: ProfileAutoUpdateStateStore,
) {
    fun readProfiles(): List<ProfileSummary> {
        val document = catalogStore.readDocumentOrNull() ?: return emptyList()
        val activePath = ProfileActiveSelectionPolicy.resolveActivePath(
            entries = document.entries,
            savedPath = catalogStore.readActivePath(),
        )
        return buildList {
            for (index in 0 until document.json.length()) {
                val profile = document.json.getJSONObject(index)
                add(profile.toProfileSummary(isActive = profile.getString("filePath") == activePath))
            }
        }
    }

    fun readActiveProfile(): ProfileSummary? {
        val document = catalogStore.readDocumentOrNull() ?: return null
        val activePath = ProfileActiveSelectionPolicy.resolveActivePath(
            entries = document.entries,
            savedPath = catalogStore.readActivePath(),
        ) ?: return null
        for (index in 0 until document.json.length()) {
            val profile = document.json.getJSONObject(index)
            if (profile.getString("filePath") == activePath) {
                return profile.toProfileSummary(isActive = true)
            }
        }
        return null
    }

    private fun JSONObject.toProfileSummary(isActive: Boolean): ProfileSummary {
        val profileId = getString("id")
        val filePath = getString("filePath")
        val typeName = optString("type", rs.chimera.android.model.ProfileType.LOCAL.name)
        val autoUpdateState = autoUpdateStateStore.read(profileId)
        val metadata = ProfileCatalogMetadataPolicy.resolve(
            storedCreatedAt = takeIf { has("createdAt") }?.optLong("createdAt"),
            storedLastUpdated = takeIf { has("lastUpdated") }?.optLong("lastUpdated"),
            storedFileSize = takeIf { has("fileSize") }?.optLong("fileSize"),
            actualFileSize = File(filePath).length(),
        )
        return ProfileSummary(
            id = profileId,
            name = getString("name"),
            filePath = filePath,
            createdAt = metadata.createdAt,
            type = if (typeName == "REMOTE") ProfileType.REMOTE else ProfileType.LOCAL,
            isActive = isActive,
            isRemote = typeName == "REMOTE",
            lastUpdated = metadata.lastUpdated,
            fileSize = metadata.fileSize,
            url = optString("url").takeIf { it.isNotBlank() },
            autoUpdate = optBoolean("autoUpdate", false),
            userAgent = optString("userAgent").takeIf { it.isNotBlank() },
            proxyUrl = optString("proxyUrl").takeIf { it.isNotBlank() },
            lastAutoUpdateAttempt = autoUpdateState.lastAttempt,
            autoUpdateFailures = autoUpdateState.failureCount,
            nextAutoUpdateAt = autoUpdateState.nextAttemptAt,
            lastAutoUpdateError = autoUpdateState.lastError,
        )
    }
}
