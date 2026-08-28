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
            val pendingMarkers = profileImportPendingMarkers(profilePrefs.all)

            ProfileImportRecoveryPolicy.recover(
                directory = filesDir,
                referencedPaths = referencedPaths,
                pendingDestinationNames = pendingMarkers.names,
            )

            val markerKeysToClear = pendingMarkers.invalidKeys +
                pendingMarkers.names.map(::profileImportPendingKey)
            if (markerKeysToClear.isNotEmpty()) {
                commitPreferences {
                    markerKeysToClear.forEach(::remove)
                }
            }
        }
    }

    fun recoverBackups() {
        val pendingMarkers = profileUpdatePendingMarkers(profilePrefs.all)

        ProfileBackupRecoveryPolicy.recover(
            directory = filesDir,
            pendingBackupNames = pendingMarkers.names,
        )

        val stalePendingKeys = pendingMarkers.invalidKeys + pendingMarkers.names
            .filterNot { name ->
                ProfileBackupRecoveryPolicy.isManagedBackupName(name) && filesDir.resolve(name).isFile
            }
            .map(::profileUpdatePendingKey)
        if (stalePendingKeys.isNotEmpty()) {
            commitPreferences {
                stalePendingKeys.forEach(::remove)
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

internal const val PROFILE_IMPORT_PENDING_PREFIX = "profile_import_pending:"
internal const val PROFILE_UPDATE_PENDING_PREFIX = "profile_update_pending:"
