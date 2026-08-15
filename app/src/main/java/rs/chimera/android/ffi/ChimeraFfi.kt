package rs.chimera.android.ffi

import rs.chimera.android.backend.BackendRuntimeState
import rs.chimera.android.service.VpnRuntimeRegistry
import uniffi.chimera_ffi.hello
import uniffi.chimera_ffi.shutdown
import uniffi.chimera_ffi.uniffiEnsureInitialized

object ChimeraFfi {
    private val initResult = runCatching {
        System.loadLibrary("chimera_ffi")
        check(nativeSetup()) { "nativeSetup returned false" }
        uniffiEnsureInitialized()
    }

    private external fun nativeSetup(): Boolean

    fun ensureInitialized() {
        initResult.getOrThrow()
    }

    @Suppress("unused")
    fun protectSocket(fd: Int): Boolean {
        return VpnRuntimeRegistry.protectSocket(fd)
    }

    @Suppress("unused")
    fun onCoreStopped(message: String) {
        val detail = message.trim().ifEmpty { "Rust core stopped unexpectedly" }
        if (!VpnRuntimeRegistry.dispatchCoreStopped(detail)) {
            BackendRuntimeState.updateServiceError(detail)
        }
    }

    fun helloOrFallback(): String {
        initResult.exceptionOrNull()?.let { error ->
            val typeName = error::class.simpleName ?: "UnknownError"
            return "FFI unavailable: $typeName"
        }

        return runCatching { hello() }
            .getOrElse { error ->
                val typeName = error::class.simpleName ?: "UnknownError"
                "FFI call failed: $typeName"
            }
    }

    fun stopCore(): Result<Unit> {
        initResult.exceptionOrNull()?.let { error ->
            return Result.failure(error)
        }

        return runCatching { shutdown() }
    }
}
