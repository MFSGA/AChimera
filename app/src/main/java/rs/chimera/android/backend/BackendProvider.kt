package rs.chimera.android.backend

object BackendProvider {
    @Suppress("UNUSED_PARAMETER")
    fun provide(): ChimeraBackend = ChimeraBackendImpl()
}