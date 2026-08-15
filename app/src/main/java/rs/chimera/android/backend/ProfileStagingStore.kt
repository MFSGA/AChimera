package rs.chimera.android.backend

import android.content.SharedPreferences
import java.io.File

internal class ProfileStagingStore(
    private val profilePrefs: SharedPreferences,
    private val filesDir: File,
    private val catalogCoordinator: ProfileCatalogCoordinator,
    private val catalogStore: ProfileCatalogStore,
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
        catalogStore.readEntriesOrEmpty()
            .mapTo(mutableSetOf()) { File(it.filePath).absolutePath }

    private fun commitPreferences(update: SharedPreferences.Editor.() -> Unit) {
        val editor = profilePrefs.edit()
        editor.update()
        ProfilePersistencePolicy.commit(persist = editor::commit)
    }
}

internal fun profileImportPendingKey(destinationName: String): String =
    "$PROFILE_IMPORT_PENDING_PREFIX$destinationName"

internal fun profileUpdatePendingKey(backupName: String): String =
    "$PROFILE_UPDATE_PENDING_PREFIX$backupName"

private const val PROFILE_IMPORT_PENDING_PREFIX = "profile_import_pending:"
private const val PROFILE_UPDATE_PENDING_PREFIX = "profile_update_pending:"
