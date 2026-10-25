package rs.chimera.android.ui.metacubex.design

import rs.chimera.android.backend.model.ProfileUpdateInterval

internal data class MetaRemoteProfileForm(
    val name: String?,
    val url: String,
    val autoUpdate: Boolean,
    val userAgent: String?,
    val proxyUrl: String?,
    val autoUpdateIntervalMillis: Long = ProfileUpdateInterval.DEFAULT_MILLIS,
    val expectedAutoUpdateIntervalMillis: Long? = null,
)
