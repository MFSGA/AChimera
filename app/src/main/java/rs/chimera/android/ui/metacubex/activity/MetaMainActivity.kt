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
import rs.chimera.android.backend.model.ProxyMode
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.ui.metacubex.design.MainDesign
import rs.chimera.android.ui.metacubex.design.formatMetaTrafficTotal
import rs.chimera.android.ui.navigation.DefaultAppUiRouter
import rs.chimera.android.ui.navigation.VpnUiActionGate
import java.util.UUID

class MetaMainActivity : AppCompatActivity() {
    companion object {
        private const val STATE_VPN_ACTION_OWNER = "vpn_action_owner"
        private const val STATE_VPN_ACTION_STAGE = "vpn_action_stage"
    }

    private enum class VpnActionStage {
        PREPARING_START,
        WAITING_FOR_PERMISSION,
        STARTING,
        STOPPING,
    }

    private val backend = BackendProvider.provide()
    private lateinit var design: MainDesign
    private var statusOperationInProgress = false
    private var vpnActionOwner: String? = null
    private var vpnActionLease: VpnUiActionGate.Lease? = null
    private var vpnActionStage: VpnActionStage? = null

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

        vpnActionOwner = savedInstanceState?.getString(STATE_VPN_ACTION_OWNER)
        vpnActionLease = vpnActionOwner?.let(VpnUiActionGate::resumeVpnAction)
        statusOperationInProgress = vpnActionLease != null
        if (statusOperationInProgress) {
            vpnActionStage = savedInstanceState
                ?.getString(STATE_VPN_ACTION_STAGE)
                ?.let { runCatching { VpnActionStage.valueOf(it) }.getOrNull() }
        } else {
            vpnActionOwner = null
            vpnActionStage = null
        }

        design = MainDesign(this)
        setContentView(design.root)
        if (statusOperationInProgress) {
            design.setStatusOperationInProgress(
                true,
                getString(
                    if (vpnActionStage == VpnActionStage.STOPPING) {
                        R.string.cmfa_service_stopping
                    } else {
                        R.string.cmfa_service_starting
                    },
                ),
            )
        }

        lifecycleScope.launch {
            for (request in design.requests) handleRequest(request)
        }
        observeBackend()
        if (statusOperationInProgress) {
            lifecycleScope.launch { resumeVpnActionAfterRecreation() }
        }
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
                            formatMetaTrafficTotal(traffic.downloadTotal + traffic.uploadTotal),
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
            MainDesign.Request.SwitchUi -> {
                if (!DefaultAppUiRouter.openWatfaq(this)) {
                    design.showToast(getString(R.string.ui_switch_wait_for_vpn_action))
                }
            }
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
        if (!beginStatusOperation(
                getString(R.string.cmfa_service_starting),
                VpnActionStage.PREPARING_START,
            )
        ) return
        continueStartPreparation()
    }

    private suspend fun continueStartPreparation() {
        try {
            when (val result = withContext(Dispatchers.IO) { backend.prepareStartVpn() }) {
                StartVpnResult.PermissionNotRequired -> startVpnAfterPermission()
                is StartVpnResult.Prepared -> {
                    vpnActionStage = VpnActionStage.WAITING_FOR_PERMISSION
                    vpnPermissionLauncher.launch(result.intent)
                }
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
        if (!statusOperationInProgress) return
        vpnActionStage = VpnActionStage.STARTING
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
        if (!beginStatusOperation(progressMessage, VpnActionStage.STOPPING)) return
        performStatusOperationBody(errorMessageRes, operation)
    }

    private suspend fun performStatusOperationBody(
        errorMessageRes: Int,
        operation: suspend () -> Unit,
    ) {
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

    private fun beginStatusOperation(
        message: String,
        stage: VpnActionStage,
    ): Boolean {
        if (statusOperationInProgress) return false
        val owner = UUID.randomUUID().toString()
        val lease = VpnUiActionGate.tryBeginVpnAction(owner) ?: return false
        vpnActionOwner = owner
        vpnActionLease = lease
        vpnActionStage = stage
        statusOperationInProgress = true
        design.setStatusOperationInProgress(true, message)
        return true
    }

    private fun finishStatusOperation() {
        val lease = vpnActionLease ?: return
        statusOperationInProgress = false
        vpnActionOwner = null
        vpnActionLease = null
        vpnActionStage = null
        VpnUiActionGate.finishVpnAction(lease)
        design.setStatusOperationInProgress(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        vpnActionOwner?.let { outState.putString(STATE_VPN_ACTION_OWNER, it) }
        vpnActionStage?.let { outState.putString(STATE_VPN_ACTION_STAGE, it.name) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (isFinishing && vpnActionStage == VpnActionStage.WAITING_FOR_PERMISSION) {
            vpnActionLease?.let(VpnUiActionGate::finishVpnAction)
            vpnActionOwner = null
            vpnActionLease = null
            vpnActionStage = null
            statusOperationInProgress = false
        }
        super.onDestroy()
    }

    private suspend fun resumeVpnActionAfterRecreation() {
        when (vpnActionStage) {
            VpnActionStage.PREPARING_START -> continueStartPreparation()
            VpnActionStage.WAITING_FOR_PERMISSION -> Unit
            VpnActionStage.STARTING -> startVpnAfterPermission()
            VpnActionStage.STOPPING -> {
                performStatusOperationBody(R.string.service_stop_failed) {
                    backend.stopVpn()
                }
            }
            null -> finishStatusOperation()
        }
    }

    private fun errorMessage(messageRes: Int, error: Exception): String =
        getString(
            messageRes,
            error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName,
        )

    private fun showAboutDialog() {
        val content = layoutInflater.inflate(R.layout.meta_dialog_about, null, false)
        content.findViewById<TextView>(R.id.about_version).text = getString(R.string.app_ver) +
            "\n" + getString(
                R.string.about_core_version,
                getString(R.string.clash_core_version),
            )
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
