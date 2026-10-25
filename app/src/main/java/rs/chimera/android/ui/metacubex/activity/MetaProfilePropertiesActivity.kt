package rs.chimera.android.ui.metacubex.activity

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.VpnSettingsAvailabilityPolicy
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.ui.metacubex.design.MetaRemoteProfilePropertiesDesign

class MetaProfilePropertiesActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private val model: MetaProfilePropertiesViewModel by viewModels()
    private var design: MetaRemoteProfilePropertiesDesign? = null
    private var pendingDraft: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        pendingDraft = savedInstanceState?.getBundle(STATE_DRAFT)
        val id = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return finish()
        setContentView(FrameLayout(this).apply {
            addView(ProgressBar(context), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = requestExit()
        })
        lifecycleScope.launch {
            try {
                val profile = model.loadProfile(id)
                    ?: throw IllegalArgumentException(getString(R.string.profile_properties_missing))
                showProfile(profile, pendingDraft)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                MaterialAlertDialogBuilder(this@MetaProfilePropertiesActivity)
                    .setTitle(R.string.profile_properties_title)
                    .setMessage(getString(R.string.profile_properties_save_failed, error.message ?: getString(R.string.profile_unknown_error)))
                    .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val draft = design?.let { page ->
            Bundle().also {
                page.saveState(it)
                it.putBoolean(STATE_DRAFT_CHANGED, page.hasChanges())
            }
        } ?: pendingDraft
        draft?.let { outState.putBundle(STATE_DRAFT, it) }
        super.onSaveInstanceState(outState)
    }

    private fun showProfile(profile: ProfileSummary, restored: Bundle?) {
        val page = MetaRemoteProfilePropertiesDesign(
            this,
            initialUrl = profile.url.orEmpty(),
            initialName = profile.name,
            initialAutoUpdate = profile.autoUpdate,
            initialAutoUpdateIntervalMillis = profile.autoUpdateIntervalMillis,
            initialUserAgent = profile.userAgent.orEmpty(),
            initialProxyUrl = profile.proxyUrl.orEmpty(),
            restoredState = restored,
            localProfile = !profile.isRemote,
            existingProfile = true,
        )
        design = page
        pendingDraft = null
        setContentView(page.root)
        lifecycleScope.launch {
            for (request in page.requests) {
                when (request) {
                    MetaRemoteProfilePropertiesDesign.Request.NavigateBack -> requestExit()
                    MetaRemoteProfilePropertiesDesign.Request.BrowseFiles ->
                        startActivity(
                            MetaProfileFilesActivity.intent(this@MetaProfilePropertiesActivity, profile.id),
                        )
                    is MetaRemoteProfilePropertiesDesign.Request.Submit -> {
                        if (canEdit(profile)) model.save(profile, request.form)
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(backend.profileDeletionAvailability, model.saveState) { _, state -> state }.collect { state ->
                    page.setEditingEnabled(canEdit(profile))
                    page.setSubmitting(state == ProfilePropertiesSaveState.Saving)
                    when (state) {
                        ProfilePropertiesSaveState.Saved -> {
                            setResult(RESULT_OK)
                            finish()
                        }
                        is ProfilePropertiesSaveState.Failed -> {
                            page.showToast(getString(R.string.profile_properties_save_failed, state.error.message ?: getString(R.string.profile_unknown_error)))
                            model.acknowledgeFailure()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun canEdit(profile: ProfileSummary): Boolean = !profile.isRemote ||
        backend.profileDeletionAvailability.value.let {
            VpnSettingsAvailabilityPolicy.canChangeSettings(it.serviceState, it.hasRunRequest)
        }

    private fun requestExit() {
        if (model.saveState.value == ProfilePropertiesSaveState.Saving) return
        val hasChanges = design?.hasChanges() ?: (pendingDraft?.getBoolean(STATE_DRAFT_CHANGED) == true)
        if (!hasChanges) return finish()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_properties_exit_title)
            .setMessage(R.string.profile_properties_exit_message)
            .setPositiveButton(R.string.profile_properties_discard) { _, _ -> finish() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_PROFILE_ID = "rs.chimera.android.profile_id"
        private const val STATE_DRAFT = "profile_properties_draft"
        private const val STATE_DRAFT_CHANGED = "profile_properties_draft_changed"
    }
}
