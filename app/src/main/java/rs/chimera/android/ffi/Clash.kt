package rs.chimera.android.ffi

data class ProfileOverride(
    val tunFd: Int,
    val logFilePath: String,
    val allowLan: Boolean = false,
    val mixedPort: Int = 7890,
    val httpPort: Int = 7891,
    val socksPort: Int = 7892,
    val someFlag: Boolean = true,
)

fun initClash(
    configPath: String,
    workDir: String,
    over: ProfileOverride,
): Result<Unit> {
    return ChimeraFfi.startCore(
        profilePath = configPath,
        cacheDir = workDir,
        tunFd = over.tunFd,
        logFilePath = over.logFilePath,
    )
}

fun shutdownClash(): Result<Unit> {
    return ChimeraFfi.stopCore()
}
