package rs.chimera.android.ffi

object ChimeraFfi {
    private const val LIB_NAME = "chimera_ffi"

    private val isLoaded: Boolean = runCatching {
        System.loadLibrary(LIB_NAME)
        true
    }.getOrElse { false }

    private external fun nativeHello(): String

    fun helloOrFallback(): String {
        if (!isLoaded) {
            return "FFI unavailable: libchimera_ffi not loaded"
        }

        return runCatching { nativeHello() }
            .getOrElse { error ->
                val typeName = error::class.simpleName ?: "UnknownError"
                "FFI call failed: $typeName"
            }
    }
}
