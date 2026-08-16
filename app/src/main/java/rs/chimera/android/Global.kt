package rs.chimera.android

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import rs.chimera.android.backend.AppForegroundState
import rs.chimera.android.service.RuntimeLogStore
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.util.PrivacySafeLog
import uniffi.chimera_ffi.ChimeraException
import java.io.File

class ChimeraApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        PrivacySafeLog.configure(
            debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
        )
        AppPreferences.apply(this)
        setupUncaughtExceptionHandler()
        AppForegroundState.register(this)
        Global.init(this)
    }
}

object Global : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    lateinit var application: ChimeraApplication
        private set

    var profilePath: String = ""
        private set

    var proxyPort: UShort? = null

    fun runtimeLogFile(): File = File(application.cacheDir, RUNTIME_LOG_FILE_NAME)

    fun readRuntimeLogTail(maxLines: Int = 160): String =
        readRuntimeLogTail(runtimeLogFile(), maxLines)

    fun clearRuntimeLog() {
        clearRuntimeLog(runtimeLogFile())
    }

    fun init(application: ChimeraApplication) {
        this.application = application
        profilePath = application
            .getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE)
            .getString(PROFILE_PATH_KEY, null)
            .orEmpty()
    }

    fun updateProfilePath(path: String) {
        profilePath = path
        application
            .getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PROFILE_PATH_KEY, path)
            .apply()
    }

    fun restoreProfilePath(): String {
        profilePath = application
            .getSharedPreferences(FILE_PREFS, Context.MODE_PRIVATE)
            .getString(PROFILE_PATH_KEY, null)
            .orEmpty()
        return profilePath
    }

    fun destroy() {
        cancel()
    }

    private const val FILE_PREFS = "file_prefs"
    private const val PROFILE_PATH_KEY = "profile_path"
    private const val RUNTIME_LOG_FILE_NAME = "chimera-rs.log"
}

internal fun readRuntimeLogTail(file: File, maxLines: Int): String =
    RuntimeLogStore.shared.readTail(file, maxLines)

internal fun clearRuntimeLog(file: File) {
    RuntimeLogStore.shared.clear(file)
}

private fun setupUncaughtExceptionHandler() {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            if (throwable is ChimeraException) {
                PrivacySafeLog.errorDetail(
                    tag = "Chimera",
                    message = "Uncaught ChimeraException",
                    debugDetail = "thread=${thread.name} message=${throwable.message.orEmpty()}",
                )
            } else {
                PrivacySafeLog.error(
                    tag = "Chimera",
                    message = "Uncaught exception",
                    error = throwable,
                    debugDetail = "thread=${thread.name}",
                )
            }
        } catch (error: Exception) {
            PrivacySafeLog.error(
                tag = "Chimera",
                message = "Error in exception handler",
                error = error,
            )
        } finally {
            defaultHandler?.uncaughtException(
                thread,
                PrivacySafeLog.crashThrowable(throwable),
            )
        }
    }
}
