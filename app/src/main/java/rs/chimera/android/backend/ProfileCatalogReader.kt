package rs.chimera.android.backend

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ProfileType

internal class ProfileCatalogReader(
    private val profilePrefs: SharedPreferences,
    private val autoUpdateStateStore: ProfileAutoUpdateStateStore,
) {
    fun readProfiles(): List<ProfileSummary> {
        val catalog = profilePrefs.getString(PROFILES_LIST_KEY, null) ?: return emptyList()
        val jsonArray = JSONArray(catalog)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                add(jsonArray.getJSONObject(index).toProfileSummary())
            }
        }
    }

    fun readActiveProfile(): ProfileSummary? {
        val savedPath = profilePrefs.getString(PROFILE_PATH_KEY, null) ?: return null
        val catalog = profilePrefs.getString(PROFILES_LIST_KEY, null) ?: return null
        val jsonArray = JSONArray(catalog)
        for (index in 0 until jsonArray.length()) {
            val profile = jsonArray.getJSONObject(index)
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

    private companion object {
        const val PROFILE_PATH_KEY = "profile_path"
        const val PROFILES_LIST_KEY = "profiles_list"
    }
}
