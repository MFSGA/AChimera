package rs.chimera.android.ui.metacubex.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.ui.metacubex.design.MetaProfileFilesDesign

internal class MetaProfileFilesActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private var profileId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
        if (profileId == null) return finish()
        setContentView(FrameLayout(this).apply {
            addView(ProgressBar(context), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        })
        lifecycleScope.launch {
            try {
                val profile = backend.listProfiles().firstOrNull { it.id == profileId }
                    ?: throw IllegalArgumentException(getString(R.string.profile_properties_missing))
                showFiles(profile.fileSize, profile.lastUpdated ?: profile.createdAt)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                MaterialAlertDialogBuilder(this@MetaProfileFilesActivity)
                    .setTitle(R.string.profile_files_title)
                    .setMessage(getString(R.string.profile_files_open_failed, error.message ?: getString(R.string.profile_unknown_error)))
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()
            }
        }
    }

    private fun showFiles(size: Long, modifiedAt: Long) {
        val id = profileId ?: return finish()
        val design = MetaProfileFilesDesign(this, size, modifiedAt)
        setContentView(design.root)
        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    MetaProfileFilesDesign.Request.NavigateBack -> finish()
                    MetaProfileFilesDesign.Request.OpenConfiguration -> openConfiguration(id)
                }
            }
        }
    }

    private suspend fun openConfiguration(id: String) {
        try {
            val contents = withContext(Dispatchers.IO) {
                backend.readProfileConfiguration(id)
            }
            showConfigurationContents(contents)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_files_configuration)
                .setMessage(getString(R.string.profile_files_open_failed, error.message ?: getString(R.string.profile_unknown_error)))
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun showConfigurationContents(contents: String) {
        val text = TextView(this).apply {
            setPadding(32, 24, 32, 24)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setTextIsSelectable(true)
            setTextDirection(TextView.TEXT_DIRECTION_FIRST_STRONG_LTR)
            text = contents
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(text)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_files_configuration)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        private const val EXTRA_PROFILE_ID = "rs.chimera.android.profile_id"

        fun intent(context: Context, profileId: String): Intent =
            Intent(context, MetaProfileFilesActivity::class.java)
                .putExtra(EXTRA_PROFILE_ID, profileId)
    }
}
