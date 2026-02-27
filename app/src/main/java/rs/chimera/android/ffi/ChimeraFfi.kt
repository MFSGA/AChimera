package rs.chimera.android.ffi

object ChimeraFfi {
    private const val LIB_NAME = "chimera_ffi"

    private val isLoaded: Boolean = runCatching {
        System.loadLibrary(LIB_NAME)
        true
    }.getOrElse { false }

    private external fun nativeHello(): String
    private external fun nativeStart(profilePath: String, cacheDir: String): Boolean
    private external fun nativeStop(): Boolean

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

    fun startCore(profilePath: String, cacheDir: String): Result<Unit> {
        if (!isLoaded) {
            return Result.failure(IllegalStateException("libchimera_ffi not loaded"))
        }
        return runCatching {
            check(nativeStart(profilePath, cacheDir)) { "nativeStart returned false" }
        }
    }

    fun stopCore(): Result<Unit> {
        if (!isLoaded) {
            return Result.success(Unit)
        }
        return runCatching {
            check(nativeStop()) { "nativeStop returned false" }
        }
    }
}
