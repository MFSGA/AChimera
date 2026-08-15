package rs.chimera.android.backend

import android.content.SharedPreferences
import org.json.JSONArray
import java.io.File

internal class ProfileStagingStore(
    private val profilePrefs: SharedPreferences,
    private val filesDir: File,
    private val catalogCoordinator: ProfileCatalogCoordinator,
) {
    fun markImportPending(destination: File) {
        commitPreferences {
            putBoolean(profileImportPendingKey(destination.name), true)
        }
    }

    fun clearImportPending(destination: File) {
        commitPreferences {
            remove(profileImportPendingKey(destination.name))
        }
    }

    fun markUpdatePending(backup: File) {
        commitPreferences {
            putBoolean(profileUpdatePendingKey(backup.name), true)
        }
    }

    fun clearUpdatePending(backup: File) {
        commitPreferences {
            remove(profileUpdatePendingKey(backup.name))
        }
    }

    fun recoverImports() {
        catalogCoordinator.withLock {
            val referencedPaths = referencedProfilePaths()
            val pendingDestinationNames = profilePrefs.all.keys
                .filter { it.startsWith(PROFILE_IMPORT_PENDING_PREFIX) }
                .mapTo(mutableSetOf()) { it.removePrefix(PROFILE_IMPORT_PENDING_PREFIX) }

            ProfileImportRecoveryPolicy.recover(
                directory = filesDir,
                referencedPaths = referencedPaths,
                pendingDestinationNames = pendingDestinationNames,
            )

            if (pendingDestinationNames.isNotEmpty()) {
                commitPreferences {
                    pendingDestinationNames.forEach { name -> remove(profileImportPendingKey(name)) }
                }
            }
        }
    }

    fun recoverBackups() {
        val pendingBackupNames = profilePrefs.all.keys
            .filter { it.startsWith(PROFILE_UPDATE_PENDING_PREFIX) }
            .mapTo(mutableSetOf()) { it.removePrefix(PROFILE_UPDATE_PENDING_PREFIX) }

        ProfileBackupRecoveryPolicy.recover(
            directory = filesDir,
            pendingBackupNames = pendingBackupNames,
        )

        val stalePendingBackups = pendingBackupNames.filterNot { name ->
            filesDir.resolve(name).isFile
        }
        if (stalePendingBackups.isNotEmpty()) {
            commitPreferences {
                stalePendingBackups.forEach { name -> remove(profileUpdatePendingKey(name)) }
            }
        }
    }

    fun recoverDeletions() {
        catalogCoordinator.withLock {
            ProfileDeletionRecoveryPolicy.recover(
                directory = filesDir,
                referencedPaths = referencedProfilePaths(),
            )
        }
    }

    private fun referencedProfilePaths(): Set<String> =
        profilePrefs.getString(PROFILES_LIST_KEY, null)
            ?.let(::JSONArray)
            ?.toCatalogEntries()
            ?.mapTo(mutableSetOf()) { File(it.filePath).absolutePath }
            .orEmpty()

    private fun commitPreferences(update: SharedPreferences.Editor.() -> Unit) {
        val editor = profilePrefs.edit()
        editor.update()
        ProfilePersistencePolicy.commit(persist = editor::commit)
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

    private companion object {
        const val PROFILES_LIST_KEY = "profiles_list"
    }
}

internal fun profileImportPendingKey(destinationName: String): String =
    "$PROFILE_IMPORT_PENDING_PREFIX$destinationName"

internal fun profileUpdatePendingKey(backupName: String): String =
    "$PROFILE_UPDATE_PENDING_PREFIX$backupName"

private const val PROFILE_IMPORT_PENDING_PREFIX = "profile_import_pending:"
private const val PROFILE_UPDATE_PENDING_PREFIX = "profile_update_pending:"
