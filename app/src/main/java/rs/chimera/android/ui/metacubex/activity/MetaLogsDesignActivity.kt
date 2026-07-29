package rs.chimera.android.ui.metacubex.activity

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.ui.metacubex.design.LogsDesign

class MetaLogsDesignActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: LogsDesign
    private var currentLog = ""
    private var paused = false
    private var autoScroll = true
    private var refreshing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        design = LogsDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) handleRequest(request)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (paused) renderLog() else design.showLoading()
                while (true) {
                    if (!paused) refreshLogs()
                    delay(2_000)
                }
            }
        }
    }

    private suspend fun handleRequest(request: LogsDesign.Request) {
        when (request) {
            is LogsDesign.Request.SetPaused -> {
                paused = request.paused
                if (!paused) refreshLogs() else renderLog()
            }
            is LogsDesign.Request.SetAutoScroll -> {
                autoScroll = request.enabled
                renderLog()
            }
            LogsDesign.Request.CopyLogs -> copyLogs()
            LogsDesign.Request.ClearLogs -> confirmClear()
            LogsDesign.Request.Retry -> refreshLogs(showLoading = true)
            LogsDesign.Request.NavigateBack -> finish()
        }
    }

    private suspend fun refreshLogs(showLoading: Boolean = false) {
        if (refreshing) return
        refreshing = true
        if (showLoading) design.showLoading()
        try {
            currentLog = withContext(Dispatchers.IO) { backend.readRuntimeLogs(500) }
            renderLog()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            design.showError(
                getString(
                    R.string.logs_read_error,
                    error.message ?: getString(R.string.profile_unknown_error),
                ),
            )
        } finally {
            refreshing = false
        }
    }

    private fun renderLog() {
        val lines = if (currentLog.isBlank()) 0 else currentLog.lineSequence().count()
        design.showContent(currentLog, lines, paused, autoScroll)
    }

    private fun copyLogs() {
        if (currentLog.isBlank()) return
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Chimera runtime log", currentLog))
        design.showToast(getString(R.string.logs_copied))
    }

    private fun confirmClear() {
        if (currentLog.isBlank()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.home_logs_clear)
            .setMessage(R.string.logs_clear_confirm)
            .setPositiveButton(R.string.home_logs_clear) { _, _ ->
                lifecycleScope.launch { clearLogs() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun clearLogs() {
        try {
            withContext(Dispatchers.IO) { backend.clearRuntimeLogs() }
            currentLog = ""
            renderLog()
            design.showToast(getString(R.string.logs_cleared))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            design.showToast(
                getString(
                    R.string.logs_clear_error,
                    error.message ?: getString(R.string.profile_unknown_error),
                ),
            )
        }
    }
}
