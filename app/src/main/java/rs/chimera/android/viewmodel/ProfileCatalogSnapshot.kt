package rs.chimera.android.viewmodel

import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.model.Profile

internal data class ProfileCatalogSnapshot(
    val profiles: List<Profile>,
    val activeProfile: Profile?,
) {
    val savedFilePath: String?
        get() = activeProfile?.filePath

    companion object {
        fun from(summaries: List<ProfileSummary>): ProfileCatalogSnapshot {
            val profiles = summaries.map { it.toProfile() }
            return ProfileCatalogSnapshot(
                profiles = profiles,
                activeProfile = profiles.firstOrNull { it.isActive },
            )
        }
    }
}
