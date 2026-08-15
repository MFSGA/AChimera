package rs.chimera.android.backend

import org.json.JSONObject
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

internal class ProfileCatalogReader(
    private val catalogStore: ProfileCatalogStore,
    private val autoUpdateStateStore: ProfileAutoUpdateStateStore,
) {
    fun readProfiles(): List<ProfileSummary> {
        val document = catalogStore.readDocumentOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until document.json.length()) {
                add(document.json.getJSONObject(index).toProfileSummary())
            }
        }
    }

    fun readActiveProfile(): ProfileSummary? {
        val savedPath = catalogStore.readActivePath() ?: return null
        val document = catalogStore.readDocumentOrNull() ?: return null
        for (index in 0 until document.json.length()) {
            val profile = document.json.getJSONObject(index)
            if (profile.getString("filePath") == savedPath || profile.getBoolean("isActive")) {
                return profile.toProfileSummary(forceActive = true)
            }
        }
        return null
    }

    private fun JSONObject.toProfileSummary(forceActive: Boolean = false): ProfileSummary {
        val profileId = getString("id")
        val typeName = optString("type", rs.chimera.android.model.ProfileType.LOCAL.name)
        val autoUpdateState = autoUpdateStateStore.read(profileId)
        return ProfileSummary(
            id = profileId,
            name = getString("name"),
            filePath = getString("filePath"),
            type = if (typeName == "REMOTE") ProfileType.REMOTE else ProfileType.LOCAL,
            isActive = forceActive || getBoolean("isActive"),
            isRemote = typeName == "REMOTE",
            lastUpdated = takeIf { has("lastUpdated") }?.getLong("lastUpdated"),
            fileSize = getLong("fileSize"),
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
