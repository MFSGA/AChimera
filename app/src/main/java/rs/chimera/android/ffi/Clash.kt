package rs.chimera.android.ffi

import uniffi.chimera_ffi.runClash
import uniffi.chimera_ffi.uniffiEnsureInitialized

typealias FinalProfile = uniffi.chimera_ffi.FinalProfile
typealias ProfileOverride = uniffi.chimera_ffi.ProfileOverride

fun initClash(
    configPath: String,
    workDir: String,
    over: ProfileOverride,
): Result<FinalProfile> {
    return runCatching {
        uniffiEnsureInitialized()
        runClash(
            configPath = configPath,
            workDir = workDir,
            over = over,
        )
    }
}

fun shutdownClash(): Result<Unit> {
    return ChimeraFfi.stopCore()
}
