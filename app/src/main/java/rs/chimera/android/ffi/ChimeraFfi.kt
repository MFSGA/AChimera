package rs.chimera.android.ffi

object ChimeraFfi {
    private const val LIB_NAME = "chimera_ffi"

    private val isLoaded: Boolean = runCatching {
        System.loadLibrary(LIB_NAME)
        true
    }.getOrElse { false }
    private val isSetup: Boolean = if (isLoaded) {
        runCatching { nativeSetup() }.getOrElse { false }
    } else {
        false
    }

    private external fun nativeSetup(): Boolean
    private external fun nativeHello(): String
    private external fun nativeStart(
        profilePath: String,
        cacheDir: String,
        tunFd: Int,
        logFilePath: String,
    ): Boolean
    private external fun nativeStop(): Boolean

    fun helloOrFallback(): String {
        if (!isLoaded) {
            return "FFI unavailable: libchimera_ffi not loaded"
        }
        if (!isSetup) {
            return "FFI unavailable: JNI setup failed"
        }

        return runCatching { nativeHello() }
            .getOrElse { error ->
                val typeName = error::class.simpleName ?: "UnknownError"
                "FFI call failed: $typeName"
            }
    }

    fun startCore(
        profilePath: String,
        cacheDir: String,
        tunFd: Int,
        logFilePath: String,
    ): Result<Unit> {
        if (!isLoaded) {
            return Result.failure(IllegalStateException("libchimera_ffi not loaded"))
        }
        if (!isSetup) {
            return Result.failure(IllegalStateException("chimera ffi setup failed"))
        }
        return runCatching {
            check(nativeStart(profilePath, cacheDir, tunFd, logFilePath)) {
                "nativeStart returned false"
            }
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
