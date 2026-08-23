package rs.chimera.android.viewmodel

import rs.chimera.android.backend.model.ProfileSummary

internal fun resolveProfileVerificationTarget(
    profiles: List<ProfileSummary>,
    savedFilePath: String?,
): String? = profiles.firstOrNull { it.isActive }?.filePath ?: savedFilePath
