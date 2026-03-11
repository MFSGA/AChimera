package rs.chimera.android.ffi

import uniffi.chimera_ffi.hello
import uniffi.chimera_ffi.shutdown
import uniffi.chimera_ffi.uniffiEnsureInitialized

object ChimeraFfi {
    private val initResult = runCatching { uniffiEnsureInitialized() }

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
