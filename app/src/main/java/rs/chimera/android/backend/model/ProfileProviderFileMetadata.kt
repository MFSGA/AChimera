package rs.chimera.android.backend.model

data class ProfileProviderFileMetadata(
    val name: String,
    val relativePath: List<String>,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long?,
)
