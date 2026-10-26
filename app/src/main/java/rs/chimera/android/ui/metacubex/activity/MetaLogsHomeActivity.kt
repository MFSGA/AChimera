package rs.chimera.android.ui.metacubex.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.RuntimeLogArchive
import rs.chimera.android.ui.metacubex.design.LogsHomeDesign

class MetaLogsHomeActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: LogsHomeDesign
    private var archiveRefreshGeneration = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        design = LogsHomeDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    LogsHomeDesign.Request.NavigateBack -> finish()
                    LogsHomeDesign.Request.OpenRuntimeLogs -> {
                        startActivity(Intent(this@MetaLogsHomeActivity, MetaLogsDesignActivity::class.java))
                    }
                    is LogsHomeDesign.Request.OpenArchive -> openArchive(request.archive)
                    LogsHomeDesign.Request.DeleteAll -> confirmDeleteAll()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { refreshArchives() }
    }

    private suspend fun refreshArchives() {
        val generation = ++archiveRefreshGeneration
        design.showArchivesLoading(generation)
        try {
            val archives = withContext(Dispatchers.IO) { backend.listRuntimeLogArchives() }
            if (generation == archiveRefreshGeneration) design.showArchives(archives, generation)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (generation == archiveRefreshGeneration) {
                design.showArchives(emptyList(), generation)
                design.showToast(
                    getString(
                        R.string.logs_read_error,
                        error.message ?: getString(R.string.profile_unknown_error),
                    ),
                )
            }
        }
    }

    private fun openArchive(archive: RuntimeLogArchive) {
        startActivity(
            Intent(this, MetaLogsDesignActivity::class.java).apply {
                putExtra(MetaLogsDesignActivity.EXTRA_ARCHIVE_INDEX, archive.index)
                putExtra(MetaLogsDesignActivity.EXTRA_ARCHIVE_FILE_NAME, archive.fileName)
                putExtra(MetaLogsDesignActivity.EXTRA_ARCHIVE_MODIFIED_AT, archive.modifiedAtEpochMillis)
                putExtra(MetaLogsDesignActivity.EXTRA_ARCHIVE_SIZE, archive.sizeBytes)
                putExtra(MetaLogsDesignActivity.EXTRA_ARCHIVE_CONTENT_SHA256, archive.contentSha256)
            },
        )
    }

    private fun confirmDeleteAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.meta_logs_clear_runtime)
            .setMessage(R.string.meta_logs_delete_runtime_confirm)
            .setPositiveButton(R.string.home_logs_clear) { _, _ ->
                lifecycleScope.launch { clearRuntimeLogs() }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun clearRuntimeLogs() {
        try {
            withContext(Dispatchers.IO) { backend.clearRuntimeLogs() }
            refreshArchives()
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
