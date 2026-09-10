package rs.chimera.android.backend

import rs.chimera.android.settings.SettingsProvider

object BackendProvider {
    private val backend: ChimeraBackend by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ChimeraBackendImpl(
            rs.chimera.android.Global.application,
            SettingsProvider.provide(rs.chimera.android.Global.application),
        )
    }

    fun provide(): ChimeraBackend = backend
}
