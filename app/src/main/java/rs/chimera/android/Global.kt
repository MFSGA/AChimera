package rs.chimera.android

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import uniffi.chimera_ffi.ChimeraException

class ChimeraApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        setupUncaughtExceptionHandler()
        Global.init(this)
    }
}

object Global : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    lateinit var application: ChimeraApplication
        private set

    var profilePath: String = ""
        private set

    var proxyPort: UShort? = null

    val isServiceRunning = MutableStateFlow(false)

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

    fun destroy() {
        cancel()
    }

    private const val FILE_PREFS = "file_prefs"
    private const val PROFILE_PATH_KEY = "profile_path"
}

private fun setupUncaughtExceptionHandler() {
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        try {
            if (throwable is ChimeraException) {
                Log.e("Chimera", "Uncaught ChimeraException on thread ${thread.name}: ${throwable.message}")
                System.err.println("Uncaught ChimeraException on thread ${thread.name}: ${throwable.message}")
            } else {
                Log.e("Chimera", "Uncaught exception on thread ${thread.name}", throwable)
            }
        } catch (error: Exception) {
            Log.e("Chimera", "Error in exception handler", error)
        } finally {
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
