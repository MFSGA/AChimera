package rs.chimera.android.viewmodel

import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.model.Profile
import rs.chimera.android.model.ProfileType

internal fun ProfileSummary.toProfile(): Profile =
    Profile(
        id = id,
        name = name,
        filePath = filePath,
        isActive = isActive,
        fileSize = fileSize,
        type = if (isRemote) ProfileType.REMOTE else ProfileType.LOCAL,
        url = url,
        lastUpdated = lastUpdated,
        autoUpdate = autoUpdate,
        userAgent = userAgent,
        proxyUrl = proxyUrl,
        lastAutoUpdateAttempt = lastAutoUpdateAttempt,
        autoUpdateFailures = autoUpdateFailures,
        nextAutoUpdateAt = nextAutoUpdateAt,
        lastAutoUpdateError = lastAutoUpdateError,
    )
