package rs.chimera.android.util

import android.util.Log

/** Keeps sensitive exception details out of release logcat while preserving debug diagnostics. */
internal object PrivacySafeLog {
    @Volatile
    private var debugLoggingEnabled = false

    fun configure(debuggable: Boolean) {
        debugLoggingEnabled = debuggable
    }

    fun error(
        tag: String,
        message: String,
        error: Throwable,
        debugDetail: String? = null,
    ) {
        if (debugLoggingEnabled) {
            Log.e(tag, withDebugDetail(message, debugDetail), error)
        } else {
            Log.e(tag, releaseMessage(message, error))
        }
    }

    fun warning(
        tag: String,
        message: String,
        error: Throwable,
        debugDetail: String? = null,
    ) {
        if (debugLoggingEnabled) {
            Log.w(tag, withDebugDetail(message, debugDetail), error)
        } else {
            Log.w(tag, releaseMessage(message, error))
        }
    }

    fun errorDetail(
        tag: String,
        message: String,
        debugDetail: String?,
    ) {
        if (debugLoggingEnabled && !debugDetail.isNullOrBlank()) {
            Log.e(tag, "$message: $debugDetail")
        } else {
            Log.e(tag, message)
        }
    }

    fun crashThrowable(error: Throwable): Throwable =
        if (debugLoggingEnabled) error else releaseCrashThrowable(error)

    internal fun releaseCrashThrowable(error: Throwable): Throwable =
        RuntimeException(errorType(error)).apply {
            stackTrace = error.stackTrace
        }

    internal fun releaseMessage(
        message: String,
        error: Throwable,
    ): String = "$message (${errorType(error)})"

    private fun errorType(error: Throwable): String =
        error.javaClass.simpleName.ifBlank { "Throwable" }

    private fun withDebugDetail(
        message: String,
        debugDetail: String?,
    ): String =
        debugDetail
            ?.takeIf(String::isNotBlank)
            ?.let { "$message: $it" }
            ?: message
}
