package rs.chimera.android.ui.metacubex.design

import rs.chimera.android.viewmodel.AppFilterMode

internal object AccessControlSelectionPolicy {
    data class Selection(
        val mode: AppFilterMode,
        val allowedApps: Set<String>,
        val disallowedApps: Set<String>,
    )

    fun selectedApps(
        mode: AppFilterMode,
        allowedApps: Set<String>,
        disallowedApps: Set<String>,
    ): Set<String> = when (mode) {
        AppFilterMode.ALL -> emptySet()
        AppFilterMode.ALLOWED -> allowedApps
        AppFilterMode.DISALLOWED -> disallowedApps
    }

    fun selection(
        mode: AppFilterMode,
        allowedApps: Set<String>,
        disallowedApps: Set<String>,
    ): Selection = when (mode) {
        AppFilterMode.ALL -> Selection(mode, emptySet(), emptySet())
        AppFilterMode.ALLOWED -> Selection(mode, allowedApps, emptySet())
        AppFilterMode.DISALLOWED -> Selection(mode, emptySet(), disallowedApps)
    }

    fun canSelectApps(mode: AppFilterMode, canChangeSettings: Boolean = true): Boolean =
        canChangeSettings && mode != AppFilterMode.ALL
}
