package rs.chimera.android.backend.model

enum class ProfileType {
    LOCAL,
    REMOTE,
}

data class ProfileSummary(
    val id: String,
    val name: String,
    val filePath: String,
    val type: ProfileType,
    val isActive: Boolean,
    val isRemote: Boolean,
    val lastUpdated: Long?,
    val fileSize: Long,
)