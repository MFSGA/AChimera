package rs.chimera.android.ui.metacubex.activity

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ProfileRemotePolicy
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.ui.metacubex.design.ProfilesDesign

class MetaProfilesDesignActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: ProfilesDesign
    private val profileLoadMutex = Mutex()
    private var operationInProgress = false

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            lifecycleScope.launch { importLocalProfile(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        design = ProfilesDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                handleRequest(request)
            }
        }

        observeProfiles()
    }

    private fun observeProfiles() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                design.showLoading()
                while (true) {
                    if (!operationInProgress) loadProfiles()
                    kotlinx.coroutines.delay(3000)
                }
            }
        }
    }

    private suspend fun loadProfiles(showLoading: Boolean = false): Boolean =
        profileLoadMutex.withLock {
            if (showLoading) design.showLoading()
            try {
                val profiles = withContext(Dispatchers.IO) { backend.listProfiles() }
                design.submitList(profiles)
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                design.showError(
                    getString(
                        R.string.profile_list_error,
                        error.message ?: getString(R.string.profile_unknown_error),
                    ),
                )
                false
            }
        }

    private suspend fun handleRequest(request: ProfilesDesign.Request) {
        when (request) {
            is ProfilesDesign.Request.SelectProfile -> performOperation(
                progressMessage = getString(R.string.profile_activating),
                successMessage = getString(R.string.profile_activate_success),
                errorMessageRes = R.string.profile_activate_error,
            ) {
                backend.activateProfile(request.profileId)
            }
            is ProfilesDesign.Request.ProfileMenu -> {
                showProfileMenu(request.profileId)
            }
            ProfilesDesign.Request.AddProfile -> {
                showAddDialog()
            }
            ProfilesDesign.Request.RefreshProfile -> updateRemoteProfiles()
            ProfilesDesign.Request.Retry -> loadProfiles(showLoading = true)
            ProfilesDesign.Request.NavigateBack -> finish()
        }
    }

    private suspend fun showProfileMenu(profileId: String) {
        if (operationInProgress) return
        val profile = try {
            withContext(Dispatchers.IO) {
                backend.listProfiles().firstOrNull { it.id == profileId }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            design.showToast(profileError(R.string.profile_list_error, error))
            return
        } ?: return
        val actions = buildList {
            if (profile.isActive) add(ProfileAction.Verify)
            if (profile.isRemote) add(ProfileAction.Update)
            add(ProfileAction.Rename)
            add(ProfileAction.Delete)
        }
        AlertDialog.Builder(this)
            .setTitle(profile.name)
            .setItems(actions.map { getString(it.labelRes) }.toTypedArray()) { _, which ->
                lifecycleScope.launch { handleProfileAction(profile, actions[which]) }
            }
            .show()
    }

    private suspend fun handleProfileAction(profile: ProfileSummary, action: ProfileAction) {
        when (action) {
            ProfileAction.Verify -> verifyProfile(profile)
            ProfileAction.Update -> performOperation(
                progressMessage = getString(R.string.profile_updating),
                successMessage = getString(R.string.profile_update_success, profile.name),
                errorMessageRes = R.string.profile_update_error,
            ) {
                backend.updateRemoteProfile(profile.id)
            }
            ProfileAction.Rename -> showRenameDialog(profile.id, profile.name)
            ProfileAction.Delete -> confirmDelete(profile.id, profile.name)
        }
    }

    private suspend fun verifyProfile(profile: ProfileSummary) {
        if (operationInProgress) return
        operationInProgress = true
        design.showOperation(getString(R.string.profile_verifying))
        try {
            val result = withContext(Dispatchers.IO) {
                backend.verifyProfile(profile.filePath).getOrThrow()
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.profile_verification_title_success)
                .setMessage(result)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AlertDialog.Builder(this)
                .setTitle(R.string.profile_verification_title_failure)
                .setMessage(
                    getString(
                        R.string.profile_verify_failure,
                        error.message ?: getString(R.string.profile_unknown_error),
                    ),
                )
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } finally {
            operationInProgress = false
        }
    }

    private fun showRenameDialog(profileId: String, currentName: String) {
        val input = EditText(this).apply {
            setText(currentName)
            setSelectAllOnFocus(true)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_rename_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        performOperation(
                            progressMessage = getString(R.string.profile_renaming),
                            successMessage = getString(R.string.profile_rename_success, newName),
                            errorMessageRes = R.string.profile_rename_error,
                        ) {
                            backend.renameProfile(profileId, newName)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(profileId: String, name: String) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setMessage(getString(R.string.profile_delete_confirm))
            .setPositiveButton(R.string.profile_delete) { _, _ ->
                lifecycleScope.launch {
                    performOperation(
                        progressMessage = getString(R.string.profile_deleting),
                        successMessage = getString(R.string.profile_delete_success, name),
                        errorMessageRes = R.string.profile_delete_error,
                    ) {
                        backend.deleteProfile(profileId)
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAddDialog() {
        if (operationInProgress) return
        val options = arrayOf(
            getString(R.string.profile_import_url),
            getString(R.string.profile_import_file),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_import)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showUrlImportDialog()
                    1 -> filePickerLauncher.launch("*/*")
                }
            }
            .show()
    }

    private fun showUrlImportDialog() {
        val input = EditText(this).apply { setText("https://") }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.profile_import_url)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = input.text.toString().trim()
                if (!ProfileRemotePolicy.isValidUrl(url)) {
                    input.error = getString(R.string.profile_url_invalid)
                    return@setOnClickListener
                }
                dialog.dismiss()
                lifecycleScope.launch {
                    performOperation(
                        progressMessage = getString(R.string.profile_importing),
                        successMessage = getString(R.string.profile_import_success, url),
                        errorMessageRes = R.string.profile_import_error,
                    ) {
                        backend.importRemoteProfile(RemoteProfileRequest(null, url))
                    }
                }
            }
        }
        dialog.show()
    }

    private suspend fun importLocalProfile(uri: Uri) {
        performOperation(
            progressMessage = getString(R.string.profile_importing),
            successMessage = getString(R.string.profile_import_success, uri.lastPathSegment ?: "profile"),
            errorMessageRes = R.string.profile_import_error,
        ) {
            backend.importLocalProfile(uri, null)
        }
    }

    private suspend fun performOperation(
        progressMessage: String,
        successMessage: String,
        errorMessageRes: Int,
        operation: suspend () -> Unit,
    ) {
        if (operationInProgress) return
        operationInProgress = true
        design.showOperation(progressMessage)
        try {
            withContext(Dispatchers.IO) { operation() }
            if (loadProfiles()) design.showToast(successMessage)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loadProfiles()
            design.showToast(profileError(errorMessageRes, error))
        } finally {
            operationInProgress = false
        }
    }

    private fun profileError(messageRes: Int, error: Exception): String =
        getString(
            messageRes,
            error.message ?: getString(R.string.profile_unknown_error),
        )

    private suspend fun updateRemoteProfiles() {
        if (operationInProgress) return
        val profiles = try {
            withContext(Dispatchers.IO) { backend.listProfiles().filter { it.isRemote } }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            design.showToast(profileError(R.string.profile_list_error, error))
            return
        }
        if (profiles.isEmpty()) {
            design.showToast(getString(R.string.profile_no_remote_profiles))
            return
        }

        operationInProgress = true
        design.showOperation(getString(R.string.profile_refreshing))
        var failed = 0
        try {
            profiles.forEach { profile ->
                try {
                    backend.updateRemoteProfile(profile.id)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed++
                }
            }
            if (loadProfiles()) {
                design.showToast(
                    getString(R.string.profile_refresh_result, profiles.size - failed, failed),
                )
            }
        } finally {
            operationInProgress = false
        }
    }

    private enum class ProfileAction(val labelRes: Int) {
        Verify(R.string.profile_verify),
        Update(R.string.profile_update),
        Rename(R.string.profile_rename),
        Delete(R.string.profile_delete),
    }
}
