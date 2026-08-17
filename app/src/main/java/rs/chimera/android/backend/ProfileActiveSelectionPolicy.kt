package rs.chimera.android.backend

internal object ProfileActiveSelectionPolicy {
    fun resolveActivePath(
        entries: List<ProfileCatalogEntry>,
        savedPath: String?,
    ): String? {
        savedPath
            ?.takeIf(String::isNotBlank)
            ?.takeIf { path -> entries.any { it.filePath == path } }
            ?.let { return it }
        return entries.firstOrNull(ProfileCatalogEntry::isActive)?.filePath
    }
}
