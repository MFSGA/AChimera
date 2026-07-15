package rs.chimera.android.backend

internal data class ProfileCatalogEntry(
    val id: String,
    val name: String,
    val filePath: String,
    val isActive: Boolean,
)

internal data class ProfileDeletion(
    val profiles: List<ProfileCatalogEntry>,
    val deletedFilePath: String,
    val shouldDeleteFile: Boolean,
)

internal object ProfileCatalogPolicy {
    fun activate(
        profiles: List<ProfileCatalogEntry>,
        id: String,
    ): List<ProfileCatalogEntry>? {
        if (profiles.none { it.id == id }) return null
        return profiles.map { it.copy(isActive = it.id == id) }
    }

    fun delete(
        profiles: List<ProfileCatalogEntry>,
        id: String,
    ): ProfileDeletion? {
        val deleted = profiles.firstOrNull { it.id == id } ?: return null
        val remaining = profiles.filterNot { it.id == id }.toMutableList()
        if (remaining.isNotEmpty() && (deleted.isActive || remaining.none { it.isActive })) {
            remaining[0] = remaining[0].copy(isActive = true)
        }

        return ProfileDeletion(
            profiles = remaining,
            deletedFilePath = deleted.filePath,
            shouldDeleteFile = remaining.none { it.filePath == deleted.filePath },
        )
    }

    fun rename(
        profiles: List<ProfileCatalogEntry>,
        id: String,
        newName: String,
    ): List<ProfileCatalogEntry>? {
        if (profiles.none { it.id == id }) return null
        return profiles.map { profile ->
            if (profile.id == id) profile.copy(name = newName) else profile
        }
    }

    fun activePath(profiles: List<ProfileCatalogEntry>): String? =
        profiles.firstOrNull { it.isActive }?.filePath
}
