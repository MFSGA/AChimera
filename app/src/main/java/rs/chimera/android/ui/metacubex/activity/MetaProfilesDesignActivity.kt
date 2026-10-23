package rs.chimera.android.ui.metacubex.activity

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.MaterialColors
import com.google.zxing.WriterException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ProfileDeletionRuntimePolicy
import rs.chimera.android.backend.ProfileUpdateRuntimeApplyResult
import rs.chimera.android.backend.updateRemoteProfilesBatch
import rs.chimera.android.backend.model.ProfileDeletionAvailability
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.RemoteProfileSettings
import rs.chimera.android.databinding.MetaDialogProfileQrCodeBinding
import rs.chimera.android.databinding.MetaDialogProfileMenuBinding
import rs.chimera.android.databinding.MetaProfileActionItemBinding
import rs.chimera.android.ui.format
import rs.chimera.android.ui.metacubex.design.ProfilesDesign
import rs.chimera.android.ui.metacubex.design.util.MetaBottomSheetDialog
import rs.chimera.android.ui.profileDetailsText
import rs.chimera.android.ui.resolveProfileAutoUpdatePresentation
import kotlin.coroutines.resume

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

    private val newProfileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        when (result.data?.getStringExtra(MetaNewProfileActivity.EXTRA_SOURCE)) {
            MetaNewProfileActivity.SOURCE_FILE -> filePickerLauncher.launch("*/*")
            MetaNewProfileActivity.SOURCE_IMPORTED -> {
                val importedName = result.data?.getStringExtra(
                    MetaNewProfileActivity.EXTRA_IMPORTED_PROFILE_NAME,
                ).orEmpty()
                lifecycleScope.launch {
                    if (loadProfiles()) {
                        design.showToast(getString(R.string.profile_import_success, importedName))
                    }
                }
            }
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    kotlinx.coroutines.delay(60_000)
                    design.refreshElapsed()
                }
            }
        }
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
        val actions = MetaProfileAction.entries.filter { it.isAvailableFor(profile) }
        var profileIsActive = backend.activeProfile.value?.id?.let { it == profileId } ?: profile.isActive
        var availability = backend.profileDeletionAvailability.value
        var deleteBlockedMessage = profileDeleteBlockedMessage(profileIsActive, availability)
        val dialog = MetaBottomSheetDialog(this)
        val binding = MetaDialogProfileMenuBinding.inflate(layoutInflater)
        val actionBindings = mutableMapOf<MetaProfileAction, MetaProfileActionItemBinding>()
        fun updateActionMenuViewport() {
            val scrollView = binding.profileActionsScrollView
            val rowWidth = scrollView.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            val rowHeights = actions.associateWith { action ->
                val actionRoot = actionBindings.getValue(action).actionRoot
                actionRoot.measure(
                    View.MeasureSpec.makeMeasureSpec(rowWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                )
                actionRoot.measuredHeight
            }
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            val parentHeight = (sheet?.parent as? View)?.height ?: 0
            val availableHeight = if (parentHeight > 0 && sheet != null) {
                parentHeight - sheet.paddingTop - sheet.paddingBottom
            } else {
                (resources.configuration.screenHeightDp * resources.displayMetrics.density).toInt()
            }
            val viewportHeight = profileActionMenuViewportHeight(actions, rowHeights, availableHeight)
            scrollView.layoutParams = scrollView.layoutParams.apply {
                height = viewportHeight
            }
        }
        fun renderDeleteAvailability(blockedMessage: String?) {
            val deleteBinding = actionBindings[MetaProfileAction.Delete] ?: return
            // Keep the CMFA action appearance; the click path and backend still reject unsafe deletes.
            deleteBinding.actionRoot.isEnabled = true
            deleteBinding.actionRoot.alpha = 1f
            deleteBinding.actionRoot.contentDescription = blockedMessage?.let {
                getString(R.string.profile_delete_blocked_action, getString(R.string.profile_delete), it)
            } ?: getString(R.string.profile_delete)
        }
        actions.forEach { action ->
            val actionBinding = MetaProfileActionItemBinding.inflate(
                layoutInflater,
                binding.profileActionsContainer,
                false,
            )
            actionBinding.actionIcon.setImageResource(action.iconRes)
            actionBinding.actionLabel.setText(action.labelRes)
            if (action == MetaProfileAction.Delete) {
                val errorColor = MaterialColors.getColor(
                    actionBinding.actionLabel,
                    com.google.android.material.R.attr.colorError,
                )
                actionBinding.actionIcon.setColorFilter(errorColor)
                actionBinding.actionLabel.setTextColor(errorColor)
            }
            actionBinding.actionRoot.setOnClickListener {
                val currentBlockMessage = if (action == MetaProfileAction.Delete) {
                    profileDeleteBlockedMessage(
                        profileIsActive = backend.activeProfile.value?.id?.let { it == profileId }
                            ?: profile.isActive,
                        availability = backend.profileDeletionAvailability.value,
                    )
                } else {
                    null
                }
                if (currentBlockMessage != null) {
                    renderDeleteAvailability(currentBlockMessage)
                    design.showToast(currentBlockMessage)
                } else {
                    dialog.dismiss()
                    lifecycleScope.launch { handleProfileAction(profile, action) }
                }
            }
            binding.profileActionsContainer.addView(actionBinding.root)
            actionBindings[action] = actionBinding
        }
        renderDeleteAvailability(deleteBlockedMessage)
        updateActionMenuViewport()
        dialog.setContentView(binding.root)
        val availabilityObserver = lifecycleScope.launch {
            combine(backend.profileDeletionAvailability, backend.activeProfile) { updatedAvailability, activeProfile ->
                updatedAvailability to (activeProfile?.id?.let { it == profileId } ?: profile.isActive)
            }.collect { (updatedAvailability, updatedProfileIsActive) ->
                availability = updatedAvailability
                profileIsActive = updatedProfileIsActive
                deleteBlockedMessage = profileDeleteBlockedMessage(profileIsActive, availability)
                renderDeleteAvailability(deleteBlockedMessage)
            }
        }
        dialog.setOnDismissListener { availabilityObserver.cancel() }
        dialog.show()
        binding.profileActionsScrollView.post { updateActionMenuViewport() }
    }

    private fun profileDeleteBlockedMessage(
        profileIsActive: Boolean,
        availability: ProfileDeletionAvailability,
    ): String? = when (
        ProfileDeletionRuntimePolicy.blockReason(
            targetIsActive = profileIsActive,
            serviceState = availability.serviceState,
            hasRunRequest = availability.hasRunRequest,
        )
    ) {
            ProfileDeletionRuntimePolicy.BlockReason.VPN_TRANSITION ->
                getString(R.string.profile_delete_blocked_transition_short)

            ProfileDeletionRuntimePolicy.BlockReason.ACTIVE_PROFILE_IN_USE ->
                getString(R.string.profile_delete_blocked_active_short)

            null -> null
    }

    private suspend fun handleProfileAction(profile: ProfileSummary, action: MetaProfileAction) {
        when (action) {
            MetaProfileAction.Details -> {
                val autoUpdateStatus = resolveProfileAutoUpdatePresentation(
                    autoUpdate = profile.autoUpdate,
                    lastAttempt = profile.lastAutoUpdateAttempt,
                    failureCount = profile.autoUpdateFailures,
                    nextAttemptAt = profile.nextAutoUpdateAt,
                    error = profile.lastAutoUpdateError,
                )?.format(this)
                val details = listOfNotNull(
                    profile.profileDetailsText(this),
                    autoUpdateStatus,
                ).joinToString("\n")
                AlertDialog.Builder(this)
                    .setTitle(profile.name)
                    .setMessage(details)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            MetaProfileAction.Verify -> verifyProfile(profile)
            MetaProfileAction.Duplicate -> duplicateProfile(profile)
            MetaProfileAction.Edit -> {
                if (profile.isRemote) {
                    showRemoteSettingsDialog(profile)
                } else {
                    showRenameDialog(profile.id, profile.name)
                }
            }
            MetaProfileAction.ShareQrCode -> showProfileQrCode(profile)
            MetaProfileAction.Update -> performOperation(
                progressMessage = getString(R.string.profile_updating),
                successMessage = getString(R.string.profile_update_success, profile.name),
                errorMessageRes = R.string.profile_update_error,
            ) {
                backend.updateRemoteProfile(profile.id)
            }
            MetaProfileAction.Delete -> confirmDelete(profile.id, profile.name)
        }
    }

    private suspend fun duplicateProfile(profile: ProfileSummary) {
        val duplicateName = profile.name
        var duplicateId: String? = null
        val succeeded = performOperation(
            progressMessage = getString(R.string.profile_duplicating),
            successMessage = getString(R.string.profile_duplicate_success, duplicateName),
            errorMessageRes = R.string.profile_duplicate_error,
        ) {
            duplicateId = backend.duplicateProfile(profile.id)
        }
        if (succeeded) {
            duplicateId?.let { showRenameDialog(it, duplicateName) }
        }
    }

    private suspend fun showProfileQrCode(profile: ProfileSummary) {
        val url = profile.url?.takeIf(String::isNotBlank) ?: return
        val bitmap = try {
            withContext(Dispatchers.Default) {
                val matrix = ProfileQrCodeEncoder.encode(url)
                val pixels = IntArray(matrix.width * matrix.height) { index ->
                    if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
                }
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            }
        } catch (_: WriterException) {
            design.showToast(getString(R.string.profile_qr_code_generation_failed))
            return
        }

        withContext(Dispatchers.Main) {
            val binding = MetaDialogProfileQrCodeBinding.inflate(layoutInflater)
            binding.qrCode.setImageBitmap(bitmap)

            suspendCancellableCoroutine { continuation ->
                val dialog = MaterialAlertDialogBuilder(this@MetaProfilesDesignActivity)
                    .setTitle(R.string.profile_share_qr_code)
                    .setView(binding.root)
                    .setPositiveButton(R.string.profile_qr_code_close) { _, _ -> }
                    .setOnDismissListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    .show()

                continuation.invokeOnCancellation { dialog.dismiss() }
            }
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

    private fun showRemoteSettingsDialog(profile: ProfileSummary) {
        showMetaRemoteProfileDialog(
            context = this,
            titleRes = R.string.profile_edit_settings,
            positiveButtonRes = R.string.save,
            initialName = profile.name,
            initialUrl = profile.url.orEmpty(),
            initialAutoUpdate = profile.autoUpdate,
            initialUserAgent = profile.userAgent.orEmpty(),
            initialProxyUrl = profile.proxyUrl.orEmpty(),
            requireName = true,
        ) { form, dismiss, setSubmitting ->
            lifecycleScope.launch {
                val succeeded = performOperation(
                    progressMessage = getString(R.string.profile_edit_settings),
                    successMessage = getString(R.string.profile_settings_saved),
                    errorMessageRes = R.string.profile_settings_error,
                ) {
                    backend.updateRemoteProfileSettings(
                        profile.id,
                        RemoteProfileSettings(
                            name = requireNotNull(form.name),
                            url = form.url,
                            autoUpdate = form.autoUpdate,
                            userAgent = form.userAgent,
                            proxyUrl = form.proxyUrl,
                        ),
                    )
                }
                if (succeeded) dismiss() else setSubmitting(false)
            }
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
        newProfileLauncher.launch(Intent(this, MetaNewProfileActivity::class.java))
    }

    private suspend fun importLocalProfile(uri: Uri) {
        var resolvedName = uri.lastPathSegment ?: "profile"
        performOperation(
            progressMessage = getString(R.string.profile_importing),
            successMessage = getString(R.string.profile_import_success, resolvedName),
            errorMessageRes = R.string.profile_import_error,
            successMessageProvider = {
                getString(R.string.profile_import_success, resolvedName)
            },
        ) {
            resolvedName = backend.importLocalProfile(uri, null)
        }
    }

    private suspend fun performOperation(
        progressMessage: String,
        successMessage: String,
        errorMessageRes: Int,
        successMessageProvider: (() -> String)? = null,
        operation: suspend () -> Unit,
    ): Boolean {
        if (operationInProgress) return false
        operationInProgress = true
        design.showOperation(progressMessage)
        try {
            withContext(Dispatchers.IO) { operation() }
            if (!loadProfiles()) return false
            design.showToast(successMessageProvider?.invoke() ?: successMessage)
            return true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            loadProfiles()
            design.showToast(profileError(errorMessageRes, error))
            return false
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
        operationInProgress = true
        design.setRefreshInProgress(true)
        try {
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

            val batch = updateRemoteProfilesBatch(
                profileIds = profiles.map { it.id },
                updateProfile = { id -> backend.updateRemoteProfile(id) },
                activeProfileId = { backend.activeProfile.value?.id },
                serviceState = { backend.serviceState.value },
                restartVpn = backend::restartVpn,
            )
            if (loadProfiles()) {
                val message = when (val runtimeApply = batch.runtimeApply) {
                    is ProfileUpdateRuntimeApplyResult.Failed -> resources.getQuantityString(
                        R.plurals.profile_refresh_reload_error,
                        batch.succeeded,
                        batch.succeeded,
                        batch.failed,
                        runtimeApply.error.message ?: getString(R.string.profile_unknown_error),
                    )
                    else -> resources.getQuantityString(
                        R.plurals.profile_refresh_result,
                        batch.succeeded,
                        batch.succeeded,
                        batch.failed,
                    )
                }
                design.showToast(message)
            }
        } finally {
            design.setRefreshInProgress(false)
            operationInProgress = false
        }
    }
}
