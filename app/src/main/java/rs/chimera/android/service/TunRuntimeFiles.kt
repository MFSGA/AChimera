package rs.chimera.android.service

import android.content.Context
import android.content.res.AssetManager
import rs.chimera.android.Global
import rs.chimera.android.util.PrivacySafeLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object TunRuntimeFiles {
    fun resolveProfilePath(context: Context): String {
        val path =
            if (Global.profilePath.isBlank()) {
                Global.restoreProfilePath()
            } else {
                Global.profilePath
            }.trim()

        if (path.isEmpty()) {
            throw IllegalStateException(context.getString(rs.chimera.android.R.string.service_profile_required))
        }

        val configFile = File(path)
        if (!configFile.exists() || !configFile.isFile) {
            throw IllegalStateException("Profile file not found: $path")
        }

        return path
    }

    fun copyRuntimeAssetsIfAvailable(
        assets: AssetManager,
        cacheDir: File,
    ) {
        listOf("Country.mmdb", "geosite.dat").forEach { name ->
            runCatching {
                assets.open("clash-res/$name").use { input ->
                    val output = File(cacheDir, name)
                    output.deleteOnExit()
                    if (!output.exists()) {
                        output.createNewFile()
                    }
                    output.outputStream().use { stream ->
                        input.copyTo(stream)
                    }
                }
            }.onFailure { error ->
                PrivacySafeLog.warning(TAG, "Runtime asset unavailable", error, debugDetail = name)
                appendRuntimeLog("runtime asset unavailable: $name", error)
            }
        }
    }

    fun appendRuntimeLog(
        message: String,
        error: Throwable? = null,
    ) {
        val file = Global.runtimeLogFile()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line =
            buildString {
                append('[')
                append(timestamp)
                append("] ")
                append(message)
                if (error != null) {
                    append(": ")
                    append(error.message ?: error.javaClass.simpleName)
                }
            }
        runCatching {
            RuntimeLogStore.shared.append(file, line)
        }
    }

    private const val TAG = "ChimeraTunService"
}
