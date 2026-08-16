package rs.chimera.android.backend

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class ProfileCatalogDocument(
    val json: JSONArray,
    val entries: List<ProfileCatalogEntry>,
) {
    val serialized: String
        get() = json.toString()
}

internal data class RemoteProfileCatalogEntry(
    val type: String,
    val url: String?,
    val userAgent: String?,
    val proxyUrl: String?,
    val filePath: String,
)

internal class ProfileCatalogStore(
    private val profilePrefs: SharedPreferences,
    private val catalogCoordinator: ProfileCatalogCoordinator,
) {
    fun readJson(): JSONArray {
        val value = profilePrefs.getString(PROFILES_LIST_KEY, null)
            ?: throw IllegalStateException("Profile catalog is empty")
        return JSONArray(value)
    }

    fun readEntriesOrEmpty(): List<ProfileCatalogEntry> =
        readDocumentOrNull()?.entries.orEmpty()

    fun readDocumentOrNull(): ProfileCatalogDocument? {
        val value = profilePrefs.getString(PROFILES_LIST_KEY, null) ?: return null
        val json = JSONArray(value)
        return ProfileCatalogDocument(
            json = json,
            entries = ProfileCatalogValidationPolicy.validate(json.toCatalogEntries()),
        )
    }

    fun readActivePath(): String? = profilePrefs.getString(PROFILE_PATH_KEY, null)

    fun readRemoteProfile(id: String): RemoteProfileCatalogEntry = catalogCoordinator.withLock {
        val profile = readJson().findById(id)
            ?: throw IllegalArgumentException("Profile not found: $id")
        RemoteProfileCatalogEntry(
            type = profile.optString("type", "LOCAL"),
            url = profile.optString("url").takeIf { it.isNotBlank() },
            userAgent = profile.optString("userAgent").takeIf { it.isNotBlank() },
            proxyUrl = profile.optString("proxyUrl").takeIf { it.isNotBlank() },
            filePath = profile.getString("filePath"),
        )
    }

    fun readDocument(): ProfileCatalogDocument =
        readDocumentOrNull() ?: throw IllegalStateException("Profile catalog is empty")

    fun render(
        document: ProfileCatalogDocument,
        entries: List<ProfileCatalogEntry>,
    ): String {
        val entriesById = entries.associateBy(ProfileCatalogEntry::id)
        val updated = JSONArray()
        for (index in 0 until document.json.length()) {
            val profile = document.json.getJSONObject(index)
            val entry = entriesById[profile.getString("id")] ?: continue
            profile.put("name", entry.name)
            profile.put("isActive", entry.isActive)
            updated.put(profile)
        }
        return updated.toString()
    }

    fun append(
        profileJson: JSONObject,
        pendingImport: File? = null,
    ): Boolean = catalogCoordinator.withLock {
        val existingJson = profilePrefs.getString(PROFILES_LIST_KEY, null)
        val jsonArray = if (existingJson != null) JSONArray(existingJson) else JSONArray()
        val isFirst = jsonArray.length() == 0
        profileJson.put("isActive", isFirst)
        jsonArray.put(profileJson)

        commitPreferences {
            putString(PROFILES_LIST_KEY, jsonArray.toString())
            if (isFirst) putString(PROFILE_PATH_KEY, profileJson.getString("filePath"))
            pendingImport?.let { remove(profileImportPendingKey(it.name)) }
        }
        isFirst
    }

    fun commitCatalog(
        catalog: String,
        activePath: String?,
    ) {
        commitPreferences {
            putString(PROFILES_LIST_KEY, catalog)
            setActivePath(activePath)
        }
    }

    fun commit(update: SharedPreferences.Editor.() -> Unit) {
        commitPreferences(update)
    }

    fun updateRemoteProfileMetadata(
        id: String,
        file: File,
        backup: File?,
        updatedAt: Long,
    ): Boolean = catalogCoordinator.withLock {
        val jsonArray = readJson()
        val profile = jsonArray.findById(id)
            ?: throw IllegalStateException("Profile disappeared during update: $id")
        profile.put("filePath", file.absolutePath)
        profile.put("fileSize", file.length())
        profile.put("lastUpdated", updatedAt)

        val activeProfile = jsonArray.findActive()
        commitUpdate(backup = backup) {
            putString(PROFILES_LIST_KEY, jsonArray.toString())
            setActivePath(activeProfile?.getString("filePath"))
        }
        activeProfile?.optString("id") == id
    }

    fun commitUpdate(
        backup: File?,
        update: SharedPreferences.Editor.() -> Unit,
    ) {
        commitPreferences {
            update()
            backup?.let { remove(profileUpdatePendingKey(it.name)) }
        }
    }

    private fun JSONArray.toCatalogEntries(): List<ProfileCatalogEntry> =
        (0 until length()).map { index ->
            val profile = getJSONObject(index)
            ProfileCatalogEntry(
                id = profile.getString("id"),
                name = profile.getString("name"),
                filePath = profile.getString("filePath"),
                isActive = profile.optBoolean("isActive", false),
            )
        }

    private fun JSONArray.findById(id: String): JSONObject? =
        (0 until length())
            .asSequence()
            .map(::getJSONObject)
            .firstOrNull { it.getString("id") == id }

    private fun JSONArray.findActive(): JSONObject? =
        (0 until length())
            .asSequence()
            .map(::getJSONObject)
            .firstOrNull { it.optBoolean("isActive", false) }

    private fun SharedPreferences.Editor.setActivePath(activePath: String?) {
        ProfileActivePathPolicy.persist(
            activePath = activePath,
            put = { putString(PROFILE_PATH_KEY, it) },
            remove = { remove(PROFILE_PATH_KEY) },
        )
    }

    private fun commitPreferences(update: SharedPreferences.Editor.() -> Unit) {
        val editor = profilePrefs.edit()
        editor.update()
        ProfilePersistencePolicy.commit(persist = editor::commit)
    }

    private companion object {
        const val PROFILE_PATH_KEY = "profile_path"
        const val PROFILES_LIST_KEY = "profiles_list"
    }
}
