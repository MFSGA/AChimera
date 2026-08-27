package rs.chimera.android.backend

import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.util.runCatchingRecoverable

internal fun refreshActiveProfileValue(
    current: ProfileSummary?,
    load: () -> ProfileSummary?,
): ProfileSummary? = runCatchingRecoverable(load).getOrElse { current }

internal fun refreshImportedActiveProfileValue(
    committedProfile: ProfileSummary,
    load: () -> ProfileSummary?,
): ProfileSummary? = refreshActiveProfileValue(
    current = committedProfile.copy(isActive = true),
    load = load,
)
