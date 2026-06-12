package rs.chimera.android.backend

object BackendProvider {
    private val backend: ChimeraBackend by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ChimeraBackendImpl()
    }

    fun provide(): ChimeraBackend = backend
}
