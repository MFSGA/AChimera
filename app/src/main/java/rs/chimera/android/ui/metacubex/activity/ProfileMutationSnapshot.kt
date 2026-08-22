package rs.chimera.android.ui.metacubex.activity

import rs.chimera.android.backend.model.ProfileSummary

internal data class ProfileMutationSnapshot(
    val id: String,
    val name: String,
    val lastUpdated: Long?,
    val isRemote: Boolean,
    val url: String?,
    val autoUpdate: Boolean,
    val userAgent: String?,
    val proxyUrl: String?,
) {
    fun matches(profile: ProfileSummary): Boolean =
        id == profile.id &&
            name == profile.name &&
            lastUpdated == profile.lastUpdated &&
            isRemote == profile.isRemote &&
            url == profile.url &&
            autoUpdate == profile.autoUpdate &&
            userAgent == profile.userAgent &&
            proxyUrl == profile.proxyUrl

    companion object {
        fun from(profile: ProfileSummary): ProfileMutationSnapshot =
            ProfileMutationSnapshot(
                id = profile.id,
                name = profile.name,
                lastUpdated = profile.lastUpdated,
                isRemote = profile.isRemote,
                url = profile.url,
                autoUpdate = profile.autoUpdate,
                userAgent = profile.userAgent,
                proxyUrl = profile.proxyUrl,
            )
    }
}
