package rs.chimera.android.ui.metacubex.activity

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.formatSize
import rs.chimera.android.ui.metacubex.design.MainDesign
import rs.chimera.android.backend.model.ProxyMode

class MetaMainActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: MainDesign
    private var statusOperationInProgress = false

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            lifecycleScope.launch { startVpnAfterPermission() }
        } else {
            lifecycleScope.launch {
                finishStatusOperation()
                design.showToast(getString(R.string.service_vpn_permission_denied))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        design = MainDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) handleRequest(request)
        }
        observeBackend()
    }

    private fun observeBackend() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    backend.runtimeStatus.collect { status ->
                        val state = status.state
                        design.setServiceState(state)
                        design.setServiceError(status.error)
                        if (state != ServiceState.RUNNING) {
                            design.setMode(getString(R.string.not_available))
                        }
                    }
                }
                launch {
                    backend.runtimeStatus
                        .map { it.state }
                        .distinctUntilChanged()
                        .combine(backend.activeProfile) { state, profile -> state to profile }
                        .collectLatest { (state, _) ->
                            if (state == ServiceState.RUNNING) {
                                val hasProviders = try {
                                    withContext(Dispatchers.IO) {
                                        backend.listProviders().isNotEmpty()
                                    }
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (_: Exception) {
                                    false
                                }
                                design.setHasProviders(hasProviders)
                            } else {
                                design.setHasProviders(false)
                            }
                        }
                }
                launch {
                    backend.activeProfile.collect { profile ->
                        design.setProfileName(profile?.name)
                    }
                }
                launch {
                    backend.traffic.collect { traffic ->
                        design.setForwarded(
                            formatSize(traffic.downloadTotal + traffic.uploadTotal),
                        )
                    }
                }
                launch {
                    backend.proxyGroups.collect { groups ->
                        if (backend.serviceState.value == ServiceState.RUNNING) {
                            val mode = MetaMainModePolicy.visibleMode(
                                backend.serviceState.value,
                                groups.firstOrNull()?.mode,
                            )
                            design.setMode(
                                mode?.let { getString(modeLabelRes(it)) }
                                    ?: getString(R.string.not_available),
                            )
                        }
                    }
                }
                launch {
                    backend.runtimeError.collect { error ->
                        if (error?.source == BackendRuntimeErrorSource.PROXY_GROUPS) {
                            design.setMode(getString(R.string.not_available))
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleRequest(request: MainDesign.Request) {
        when (request) {
            MainDesign.Request.ToggleStatus -> toggleService()
            MainDesign.Request.OpenProxy -> {
                startActivity(Intent(this, MetaProxyDesignActivity::class.java))
            }
            MainDesign.Request.OpenProviders -> {
                startActivity(Intent(this, MetaProvidersActivity::class.java))
            }
            MainDesign.Request.OpenProfiles -> {
                startActivity(Intent(this, MetaProfilesDesignActivity::class.java))
            }
            MainDesign.Request.OpenLogs -> {
                startActivity(Intent(this, MetaLogsHomeActivity::class.java))
            }
            MainDesign.Request.OpenSettings -> {
                startActivity(Intent(this, MetaSettingsHomeActivity::class.java))
            }
            MainDesign.Request.OpenHelp -> {
                startActivity(Intent(this, MetaHelpActivity::class.java))
            }
            MainDesign.Request.OpenAbout -> showAboutDialog()
        }
    }

    private suspend fun toggleService() {
        if (statusOperationInProgress) return
        when (backend.serviceState.value) {
            ServiceState.RUNNING -> performStatusOperation(
                progressMessage = getString(R.string.cmfa_service_stopping),
                errorMessageRes = R.string.service_stop_failed,
            ) {
                backend.stopVpn()
            }
            ServiceState.STOPPED,
            ServiceState.ERROR -> prepareVpnStart()
            ServiceState.STARTING,
            ServiceState.STOPPING -> Unit
        }
    }

    private suspend fun prepareVpnStart() {
        beginStatusOperation(getString(R.string.cmfa_service_starting))
        try {
            when (val result = withContext(Dispatchers.IO) { backend.prepareStartVpn() }) {
                StartVpnResult.PermissionNotRequired -> startVpnAfterPermission()
                is StartVpnResult.Prepared -> vpnPermissionLauncher.launch(result.intent)
                is StartVpnResult.Error -> {
                    finishStatusOperation()
                    design.showToast(result.message)
                }
            }
        } catch (error: CancellationException) {
            finishStatusOperation()
            throw error
        } catch (error: Exception) {
            finishStatusOperation()
            design.showToast(errorMessage(R.string.service_start_failed, error))
        }
    }

    private suspend fun startVpnAfterPermission() {
        try {
            withContext(Dispatchers.IO) { backend.startVpnAfterPermission() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            design.showToast(errorMessage(R.string.service_start_failed, error))
        } finally {
            finishStatusOperation()
        }
    }

    private suspend fun performStatusOperation(
        progressMessage: String,
        errorMessageRes: Int,
        operation: suspend () -> Unit,
    ) {
        beginStatusOperation(progressMessage)
        try {
            withContext(Dispatchers.IO) { operation() }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            design.showToast(errorMessage(errorMessageRes, error))
        } finally {
            finishStatusOperation()
        }
    }

    private fun beginStatusOperation(message: String) {
        statusOperationInProgress = true
        design.setStatusOperationInProgress(true, message)
    }

    private fun finishStatusOperation() {
        statusOperationInProgress = false
        design.setStatusOperationInProgress(false)
    }

    private fun errorMessage(messageRes: Int, error: Exception): String =
        getString(
            messageRes,
            error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName,
        )

    private fun showAboutDialog() {
        val content = layoutInflater.inflate(R.layout.meta_dialog_about, null, false)
        content.findViewById<TextView>(R.id.about_version).text = getString(R.string.app_ver)
        AlertDialog.Builder(this)
            .setView(content)
            .show()
    }

    private fun modeLabelRes(mode: ProxyMode): Int =
        when (mode) {
            ProxyMode.RULE -> R.string.cmfa_main_mode_rule
            ProxyMode.GLOBAL -> R.string.cmfa_main_mode_global
            ProxyMode.DIRECT -> R.string.cmfa_main_mode_direct
        }
}
