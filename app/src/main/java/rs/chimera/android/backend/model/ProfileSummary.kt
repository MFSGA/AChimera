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
    val url: String? = null,
    val autoUpdate: Boolean = false,
    val userAgent: String? = null,
    val proxyUrl: String? = null,
    val lastAutoUpdateAttempt: Long? = null,
    val autoUpdateFailures: Int = 0,
    val nextAutoUpdateAt: Long? = null,
    val lastAutoUpdateError: String? = null,
)
