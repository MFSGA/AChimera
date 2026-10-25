package rs.chimera.android.ui.metacubex.activity

import android.content.ClipboardManager
import android.os.Build
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
import rs.chimera.android.ui.metacubex.design.RuntimeLogEntry
import rs.chimera.android.util.SensitiveClipboard

class MetaLogsDesignActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: LogsDesign
    private var refreshing = false
    private var hasLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        design = LogsDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    is LogsDesign.Request.CopyMessage -> copyMessage(request.message)
                    LogsDesign.Request.Retry -> refreshLogs(showLoading = true)
                    LogsDesign.Request.NavigateBack, LogsDesign.Request.Close -> finish()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!hasLoaded) design.showLoading()
                while (true) {
                    refreshLogs()
                    delay(2_000)
                }
            }
        }
    }

    private suspend fun refreshLogs(showLoading: Boolean = false) {
        if (refreshing) return
        refreshing = true
        if (showLoading) design.showLoading()
        try {
            val messages = withContext(Dispatchers.IO) { RuntimeLogEntry.parse(backend.readRuntimeLogs(500)) }
            design.showContent(messages)
            hasLoaded = true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            design.showError(getString(R.string.logs_read_error, error.message ?: getString(R.string.profile_unknown_error)))
        } finally {
            refreshing = false
        }
    }

    private suspend fun copyMessage(message: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(
            SensitiveClipboard.plainText(getString(R.string.meta_logcat), message),
        )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            design.showToast(getString(R.string.meta_log_message_copied))
        }
    }
}
