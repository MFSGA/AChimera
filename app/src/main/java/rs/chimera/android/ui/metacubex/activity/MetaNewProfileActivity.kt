package rs.chimera.android.ui.metacubex.activity

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRError
import io.github.g00fy2.quickie.QRResult.QRMissingPermission
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.QRResult.QRUserCanceled
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.ProfileRemotePolicy
import rs.chimera.android.backend.model.RemoteProfileRequest
import rs.chimera.android.ui.metacubex.design.MetaNewProfileDesign
import rs.chimera.android.ui.metacubex.design.MetaRemoteProfileForm
import rs.chimera.android.ui.metacubex.design.MetaRemoteProfilePropertiesDesign

class MetaNewProfileActivity : AppCompatActivity() {
    companion object {
        const val ACTION_PROVIDE_URL = "com.github.kr328.clash.action.PROVIDE_URL"
        const val EXTRA_PROVIDER_NAME = "name"
        const val EXTRA_SOURCE = "rs.chimera.android.ui.metacubex.profile_source"
        const val SOURCE_FILE = "file"
        const val SOURCE_IMPORTED = "remote_imported"
        const val EXTRA_IMPORTED_PROFILE_NAME = "rs.chimera.android.ui.metacubex.imported_profile_name"
        private const val STATE_PROPERTIES_OPEN = "remote_profile_properties_open"
        private const val STATE_PROVIDER_SNAPSHOT = "external_provider_snapshot"
    }

    private val importViewModel: MetaNewProfileViewModel by viewModels()
    private lateinit var design: MetaNewProfileDesign
    private var propertiesDesign: MetaRemoteProfilePropertiesDesign? = null
    private var requestJob: Job? = null
    private var importingProfile = false
    private var providerSnapshot = false
    private val externalProviderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uri = data.data ?: return@registerForActivityResult
        if (importingProfile) return@registerForActivityResult
        val name = data.getStringExtra(EXTRA_PROVIDER_NAME)
        handleExternalProfileResult(uri, name)
    }
    private val scanLauncher = registerForActivityResult(ScanQRCode(), ::handleScanResult)
    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (importingProfile) return
            if (propertiesDesign == null) finish() else requestPropertiesExit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)
        onBackPressedDispatcher.addCallback(this, backCallback)
        if (savedInstanceState?.getBoolean(STATE_PROPERTIES_OPEN) == true) {
            showProperties(
                initialUrl = savedInstanceState.getString(
                    MetaRemoteProfilePropertiesDesign.STATE_ORIGINAL_URL,
                    "",
                ),
                snapshotMode = savedInstanceState.getBoolean(STATE_PROVIDER_SNAPSHOT),
                restoredState = savedInstanceState,
            )
        } else {
            showChooser()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                importViewModel.importState.collect(::renderRemoteProfileImportState)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_PROPERTIES_OPEN, propertiesDesign != null)
        outState.putBoolean(STATE_PROVIDER_SNAPSHOT, providerSnapshot)
        propertiesDesign?.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    private fun showChooser() {
        requestJob?.cancel()
        propertiesDesign = null
        providerSnapshot = false
        if (!::design.isInitialized) design = MetaNewProfileDesign(this)
        setContentView(design.root)
        lifecycleScope.launch {
            design.setExternalProviders(queryExternalProviders())
        }
        requestJob = lifecycleScope.launch {
            for (request in design.requests) {
                if (importingProfile) continue
                when (request) {
                    MetaNewProfileDesign.Request.NavigateBack -> finish()
                    MetaNewProfileDesign.Request.ImportFile -> returnSource(SOURCE_FILE)
                    MetaNewProfileDesign.Request.ImportUrl -> showProperties("")
                    MetaNewProfileDesign.Request.ImportQrCode -> scanLauncher.launch(null)
                    is MetaNewProfileDesign.Request.OpenExternalProvider -> {
                        val intent = Intent(ACTION_PROVIDE_URL).setComponent(request.componentName)
                        runCatching { externalProviderLauncher.launch(intent) }
                            .onFailure { error ->
                                design.showToast(
                                    getString(
                                        R.string.profile_import_error,
                                        error.message ?: getString(R.string.profile_unknown_error),
                                    ),
                                )
                            }
                    }
                    is MetaNewProfileDesign.Request.OpenExternalProviderDetails -> {
                        startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", request.packageName, null)
                            },
                        )
                    }
                }
            }
        }
    }

    private suspend fun queryExternalProviders(): List<MetaNewProfileDesign.ExternalProvider> =
        withContext(Dispatchers.IO) {
            packageManager.queryIntentActivities(Intent(ACTION_PROVIDE_URL), 0)
                .mapNotNull { resolveInfo ->
                    val activity = resolveInfo.activityInfo ?: return@mapNotNull null
                    MetaNewProfileDesign.ExternalProvider(
                        packageName = activity.packageName,
                        name = activity.applicationInfo.loadLabel(packageManager).toString(),
                        summary = activity.loadLabel(packageManager).toString(),
                        icon = activity.loadIcon(packageManager),
                        componentName = ComponentName(activity.packageName, activity.name),
                    )
                }
        }

    private fun handleExternalProfileResult(uri: Uri, name: String?) {
        val url = profileProviderResultUrl(uri.toString())
        if (url == null) {
            lifecycleScope.launch { design.showToast(getString(R.string.profile_url_invalid)) }
            return
        }
        showProperties(
            initialUrl = url,
            initialName = profileProviderResultName(name, getString(R.string.profile_properties_new_name)),
            snapshotMode = true,
        )
    }

    private fun showProperties(
        initialUrl: String,
        initialName: String? = null,
        snapshotMode: Boolean = false,
        restoredState: Bundle? = null,
    ) {
        requestJob?.cancel()
        providerSnapshot = restoredState?.getBoolean(STATE_PROVIDER_SNAPSHOT) ?: snapshotMode
        val page = MetaRemoteProfilePropertiesDesign(
            context = this,
            initialUrl = initialUrl,
            initialName = restoredState?.getString(
                MetaRemoteProfilePropertiesDesign.STATE_ORIGINAL_NAME,
            ) ?: initialName ?: getString(R.string.profile_properties_new_name),
            initialAutoUpdate = restoredState?.getBoolean(
                MetaRemoteProfilePropertiesDesign.STATE_ORIGINAL_AUTO_UPDATE,
            ) ?: false,
            initialUserAgent = restoredState?.getString(
                MetaRemoteProfilePropertiesDesign.STATE_ORIGINAL_USER_AGENT,
            ).orEmpty(),
            initialProxyUrl = restoredState?.getString(
                MetaRemoteProfilePropertiesDesign.STATE_ORIGINAL_PROXY_URL,
            ).orEmpty(),
            restoredState = restoredState,
            snapshotMode = providerSnapshot,
        )
        propertiesDesign = page
        setContentView(page.root)
        importingProfile = importViewModel.importState.value == RemoteProfileImportState.Importing
        page.setSubmitting(importingProfile)
        requestJob = lifecycleScope.launch {
            for (request in page.requests) {
                when (request) {
                    MetaRemoteProfilePropertiesDesign.Request.NavigateBack -> requestPropertiesExit()
                    MetaRemoteProfilePropertiesDesign.Request.BrowseFiles -> requestPropertiesBrowseFiles()
                    is MetaRemoteProfilePropertiesDesign.Request.Submit -> submitRemoteProfile(request.form)
                }
            }
        }
    }

    private fun requestPropertiesExit() {
        val page = propertiesDesign ?: return finish()
        if (importingProfile) return
        if (!page.hasChanges()) {
            showChooser()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_properties_exit_title)
            .setMessage(R.string.profile_properties_exit_message)
            .setPositiveButton(R.string.profile_properties_discard) { _, _ -> showChooser() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun requestPropertiesBrowseFiles() {
        val page = propertiesDesign ?: return
        if (importingProfile) return
        if (!page.hasChanges()) {
            returnSource(SOURCE_FILE)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profile_properties_exit_title)
            .setMessage(R.string.profile_properties_exit_message)
            .setPositiveButton(R.string.profile_properties_discard) { _, _ ->
                returnSource(SOURCE_FILE)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun submitRemoteProfile(form: MetaRemoteProfileForm) {
        val page = propertiesDesign ?: return
        if (importingProfile) return
        val accepted = importViewModel.submit(
            RemoteProfileRequest(
                name = form.name,
                url = form.url,
                autoUpdate = form.autoUpdate && !providerSnapshot,
                autoUpdateIntervalMillis = form.autoUpdateIntervalMillis,
                userAgent = form.userAgent,
                proxyUrl = form.proxyUrl,
            ),
            asLocalSnapshot = providerSnapshot,
        )
        if (accepted) {
            importingProfile = true
            page.setSubmitting(true)
        }
    }

    private fun renderRemoteProfileImportState(state: RemoteProfileImportState) {
        when (state) {
            RemoteProfileImportState.Idle -> importingProfile = false
            RemoteProfileImportState.Importing -> {
                importingProfile = true
                propertiesDesign?.setSubmitting(true)
            }
            is RemoteProfileImportState.Completed -> {
                importingProfile = false
                setResult(
                    RESULT_OK,
                    Intent()
                        .putExtra(EXTRA_SOURCE, SOURCE_IMPORTED)
                        .putExtra(EXTRA_IMPORTED_PROFILE_NAME, state.name),
                )
                finish()
            }
            is RemoteProfileImportState.Failed -> {
                importingProfile = false
                propertiesDesign?.let { page ->
                    page.showToast(
                        getString(
                            R.string.profile_import_error,
                            state.error.message ?: getString(R.string.profile_unknown_error),
                        ),
                    )
                    page.setSubmitting(false)
                }
                importViewModel.acknowledgeFailure()
            }
        }
    }

    private fun handleScanResult(result: QRResult) {
        when (result) {
            is QRSuccess -> {
                val url = profileUrlFromQr(result.content.rawValue, result.content.rawBytes)
                if (url == null) {
                    lifecycleScope.launch {
                        design.showToast(getString(R.string.profile_qr_empty_result))
                    }
                } else {
                    showProperties(url)
                }
            }
            QRUserCanceled -> Unit
            QRMissingPermission -> lifecycleScope.launch {
                design.showToast(getString(R.string.profile_qr_no_camera_permission))
            }
            is QRError -> lifecycleScope.launch {
                design.showToast(getString(R.string.profile_qr_scan_failed))
            }
        }
    }

    private fun returnSource(source: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_SOURCE, source))
        finish()
    }
}

internal fun profileUrlFromQr(rawValue: String?, rawBytes: ByteArray?): String? =
    rawValue?.takeIf(String::isNotBlank)
        ?: rawBytes?.let { String(it) }?.takeIf(String::isNotBlank)

internal fun profileProviderResultName(name: String?, fallbackName: String): String =
    name?.takeIf(String::isNotBlank) ?: fallbackName

internal fun profileProviderResultUrl(value: String): String? =
    value.takeIf(ProfileRemotePolicy::isValidUrl)
