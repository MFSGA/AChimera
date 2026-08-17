package rs.chimera.android.backend

internal data class ProfileCatalogMetadata(
    val createdAt: Long,
    val lastUpdated: Long?,
    val fileSize: Long,
)

internal object ProfileCatalogMetadataPolicy {
    fun resolve(
        storedCreatedAt: Long?,
        storedLastUpdated: Long?,
        storedFileSize: Long?,
        actualFileSize: Long,
    ): ProfileCatalogMetadata =
        ProfileCatalogMetadata(
            createdAt = storedCreatedAt?.coerceAtLeast(0L) ?: 0L,
            lastUpdated = storedLastUpdated?.takeIf { it > 0L },
            fileSize = storedFileSize?.coerceAtLeast(0L) ?: actualFileSize.coerceAtLeast(0L),
        )
}
