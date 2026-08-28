package rs.chimera.android.backend

internal data class ProfilePendingMarkers(
    val names: Set<String>,
    val invalidKeys: Set<String>,
)

internal fun profileImportPendingMarkers(entries: Map<String, *>): ProfilePendingMarkers =
    profilePendingMarkers(entries, PROFILE_IMPORT_PENDING_PREFIX)

internal fun profileUpdatePendingMarkers(entries: Map<String, *>): ProfilePendingMarkers =
    profilePendingMarkers(entries, PROFILE_UPDATE_PENDING_PREFIX)

internal fun profileUpdatePendingKeysForTarget(
    entries: Map<String, *>,
    targetName: String,
): Set<String> =
    entries.keys
        .asSequence()
        .filter { it.startsWith(PROFILE_UPDATE_PENDING_PREFIX) }
        .filter { key ->
            val backupName = key.removePrefix(PROFILE_UPDATE_PENDING_PREFIX)
            ProfileBackupRecoveryPolicy.managedBackupTargetName(backupName) == targetName
        }
        .toSet()

private fun profilePendingMarkers(
    entries: Map<String, *>,
    prefix: String,
): ProfilePendingMarkers {
    val names = mutableSetOf<String>()
    val invalidKeys = mutableSetOf<String>()
    entries.forEach { (key, value) ->
        if (!key.startsWith(prefix)) return@forEach
        if (value == true) {
            names += key.removePrefix(prefix)
        } else {
            invalidKeys += key
        }
    }
    return ProfilePendingMarkers(
        names = names,
        invalidKeys = invalidKeys,
    )
}
