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
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.ProfileFileMetadata
import rs.chimera.android.backend.model.ProfileProviderFileMetadata
import rs.chimera.android.ui.metacubex.design.MetaProfileFilesDesign

internal class MetaProfileFilesActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private var profileId: String? = null
    private var profileMetadata: ProfileFileMetadata? = null
    private var providerPath: List<String>? = null
    private var filesDesign: MetaProfileFilesDesign? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val id = intent.getStringExtra(EXTRA_PROFILE_ID)
        profileId = id
        if (id == null) return finish()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val design = filesDesign ?: return finish()
                if (design.requests.trySend(MetaProfileFilesDesign.Request.NavigateBack).isFailure) {
                    finish()
                }
            }
        })
        setContentView(FrameLayout(this).apply {
            addView(ProgressBar(context), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        })
        lifecycleScope.launch {
            try {
                showFiles(backend.readProfileFileMetadata(id))
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

    private fun showFiles(metadata: ProfileFileMetadata) {
        val id = profileId ?: return finish()
        val design = MetaProfileFilesDesign(this, metadata)
        profileMetadata = metadata
        filesDesign = design
        setContentView(design.root)
        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    MetaProfileFilesDesign.Request.NavigateBack -> navigateBack(id)
                    MetaProfileFilesDesign.Request.OpenConfiguration -> openConfiguration(id)
                    is MetaProfileFilesDesign.Request.OpenProviderDirectory ->
                        showProviderDirectory(id, request.path)
                }
            }
        }
    }

    private suspend fun navigateBack(id: String) {
        val currentPath = providerPath ?: return finish()
        if (currentPath.isEmpty()) {
            providerPath = null
            profileMetadata?.let { filesDesign?.showProfileRoot(it) }
        } else {
            showProviderDirectory(id, currentPath.dropLast(1))
        }
    }

    private suspend fun showProviderDirectory(id: String, path: List<String>) {
        try {
            val files: List<ProfileProviderFileMetadata> = backend.listProfileProviderFiles(id, path)
            providerPath = path.toList()
            filesDesign?.showProviderDirectory(files)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.profile_files_providers)
                .setMessage(getString(R.string.profile_files_list_failed, error.message ?: getString(R.string.profile_unknown_error)))
                .setPositiveButton(android.R.string.ok, null)
                .show()
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
