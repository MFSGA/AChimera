package rs.chimera.android.backend

import android.content.Context
import android.content.SharedPreferences

internal open class ProxySelectionException(message: String) : IllegalStateException(message)

internal class ProxySelectionPersistenceException(message: String) : ProxySelectionException(message)

internal class ProxySelectionProfileReloadRequiredException(message: String) : ProxySelectionException(message)

/** Stores user-selected selector members separately from the imported profile configuration. */
internal class ProxySelectionStore(
    private val preferences: SharedPreferences,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
    )

    fun read(profileId: String, profileRevision: Long?): ProxySelectionRead = synchronized(LOCK) {
        val values = preferences.all
        val revisionMatches = values[revisionKey(profileId)] == revisionToken(profileRevision)
        val hasSelections = values.keys.any {
            it.startsWith(profilePrefix(profileId)) && it != revisionKey(profileId)
        }
        if (!revisionMatches) {
            return@synchronized ProxySelectionRead(
                selections = emptyMap(),
                revisionMatches = false,
                hasSelections = hasSelections,
            )
        }
        values.entries
            .asSequence()
            .filter { (key, value) -> key.startsWith(selectionPrefix(profileId)) && value is String }
            .map { (key, value) -> key.removePrefix(selectionPrefix(profileId)) to (value as String) }
            .sortedBy { (groupName, _) -> groupName }
            .toMap()
            .let { selections ->
                ProxySelectionRead(
                    selections = selections,
                    revisionMatches = true,
                    hasSelections = hasSelections,
                )
            }
    }

    fun save(
        profileId: String,
        profileRevision: Long?,
        groupName: String,
        proxyName: String,
    ): Boolean = synchronized(LOCK) {
        val revision = revisionToken(profileRevision)
        val revisionKey = revisionKey(profileId)
        val revisionChanged = preferences.all[revisionKey] != revision
        val staleKeys = if (revisionChanged) {
            val currentSelectionKey = selectionKey(profileId, groupName)
            preferences.all.keys.filter {
                it.startsWith(profilePrefix(profileId)) &&
                    it != revisionKey &&
                    it != currentSelectionKey
            }
        } else {
            emptyList()
        }
        ProfilePersistencePolicy.tryCommitWithRetry {
            val editor = preferences.edit()
            staleKeys.forEach(editor::remove)
            editor.putString(revisionKey, revision)
                .putString(selectionKey(profileId, groupName), proxyName)
                .commit()
        }
    }

    fun removeGroups(profileId: String, groupNames: Collection<String>): Boolean = synchronized(LOCK) {
        val keys = groupNames.map { selectionKey(profileId, it) }
        removeKeys(keys)
    }

    fun removeProfile(profileId: String): Boolean = synchronized(LOCK) {
        val prefix = profilePrefix(profileId)
        removeKeys(preferences.all.keys.filter { it.startsWith(prefix) })
    }

    private fun removeKeys(keys: Collection<String>): Boolean {
        if (keys.isEmpty()) return true
        return ProfilePersistencePolicy.tryCommitWithRetry {
            val editor = preferences.edit()
            keys.forEach(editor::remove)
            editor.commit()
        }
    }

    private fun profilePrefix(profileId: String): String = "$KEY_PREFIX${profileId.length}:$profileId:"

    private fun selectionPrefix(profileId: String): String = profilePrefix(profileId) + SELECTION_PREFIX

    private fun revisionKey(profileId: String): String = profilePrefix(profileId) + REVISION_KEY

    private fun selectionKey(profileId: String, groupName: String): String =
        selectionPrefix(profileId) + groupName

    private fun revisionToken(profileRevision: Long?): String = profileRevision?.toString() ?: "local"

    private companion object {
        const val PREFERENCES_NAME = "proxy_selections"
        const val KEY_PREFIX = "profile:"
        const val SELECTION_PREFIX = "selection:"
        const val REVISION_KEY = "revision"
        val LOCK = Any()
    }
}

internal data class ProxySelectionRead(
    val selections: Map<String, String>,
    val revisionMatches: Boolean,
    val hasSelections: Boolean,
)
