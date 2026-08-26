package rs.chimera.android.ffi

internal inline fun <T> runCatchingNativeBoundary(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: UnsatisfiedLinkError) {
        Result.failure(error)
    } catch (error: Exception) {
        Result.failure(error)
    }
