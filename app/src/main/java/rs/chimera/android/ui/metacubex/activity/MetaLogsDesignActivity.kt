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
import rs.chimera.android.backend.model.RuntimeLogArchive
import rs.chimera.android.ui.metacubex.design.LogsDesign
import rs.chimera.android.ui.metacubex.design.RuntimeLogEntry
import rs.chimera.android.util.SensitiveClipboard

class MetaLogsDesignActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private val archive: RuntimeLogArchive? by lazy(LazyThreadSafetyMode.NONE) {
        if (!intent.hasExtra(EXTRA_ARCHIVE_INDEX)) {
            null
        } else {
            RuntimeLogArchive(
                index = intent.getIntExtra(EXTRA_ARCHIVE_INDEX, -1),
                fileName = intent.getStringExtra(EXTRA_ARCHIVE_FILE_NAME).orEmpty(),
                modifiedAtEpochMillis = intent.getLongExtra(EXTRA_ARCHIVE_MODIFIED_AT, Long.MIN_VALUE),
                sizeBytes = intent.getLongExtra(EXTRA_ARCHIVE_SIZE, -1),
                contentSha256 = intent.getStringExtra(EXTRA_ARCHIVE_CONTENT_SHA256).orEmpty(),
            )
        }
    }
    private lateinit var design: LogsDesign
    private var refreshing = false
    private var hasLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        design = LogsDesign(this)
        if (archive != null) design.setArchiveMode()
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    is LogsDesign.Request.CopyMessage -> copyMessage(request.message)
                    LogsDesign.Request.Retry -> {
                        if (archive == null) {
                            refreshLogs(showLoading = true)
                        } else {
                            finish()
                        }
                    }
                    LogsDesign.Request.NavigateBack, LogsDesign.Request.Close -> finish()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (archive != null) {
                    if (!hasLoaded) {
                        design.showLoading()
                        loadArchive()
                    }
                } else {
                    if (!hasLoaded) design.showLoading()
                    while (true) {
                        refreshLogs()
                        delay(2_000)
                    }
                }
            }
        }
    }

    private suspend fun loadArchive(showLoading: Boolean = false) {
        if (refreshing) return
        refreshing = true
        if (showLoading) design.showLoading()
        try {
            val archived = archive ?: return
            val messages = withContext(Dispatchers.IO) {
                RuntimeLogEntry.parse(backend.readRuntimeLogArchive(archived))
            }
            design.showContent(messages)
            hasLoaded = true
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

    companion object {
        const val EXTRA_ARCHIVE_INDEX = "runtime_log_archive_index"
        const val EXTRA_ARCHIVE_FILE_NAME = "runtime_log_archive_file_name"
        const val EXTRA_ARCHIVE_MODIFIED_AT = "runtime_log_archive_modified_at"
        const val EXTRA_ARCHIVE_SIZE = "runtime_log_archive_size"
        const val EXTRA_ARCHIVE_CONTENT_SHA256 = "runtime_log_archive_content_sha256"
    }
}
