package rs.chimera.android.ui.metacubex.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import rs.chimera.android.R
import rs.chimera.android.Global
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.ui.metacubex.design.MainDesign
import rs.chimera.android.formatSize
import kotlinx.coroutines.*

class MetaMainActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: MainDesign
    private var job: Job? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            CoroutineScope(Dispatchers.Default).launch {
                backend.startVpnAfterPermission()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        design = MainDesign(this)
        setContentView(design.root)

        job = CoroutineScope(Dispatchers.Default).launch {
            for (request in design.requests) {
                handleRequest(request)
            }
        }

        observeBackend()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun observeBackend() {
        CoroutineScope(Dispatchers.Default).launch {
            backend.serviceState.collect { state ->
                val running = state == ServiceState.RUNNING
                withContext(Dispatchers.Main) {
                    design.setClashRunning(running)
                }
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            backend.activeProfile.collect { profile ->
                withContext(Dispatchers.Main) {
                    design.setProfileName(profile?.name)
                }
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            backend.traffic.collect { traffic ->
                val total = formatSize(traffic.downloadTotal + traffic.uploadTotal)
                withContext(Dispatchers.Main) {
                    design.setForwarded(total)
                }
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                if (backend.serviceState.value == ServiceState.RUNNING) {
                    val mode = runCatching {
                        backend.listProxyGroups().firstOrNull()?.mode?.name ?: "Rule"
                    }.getOrDefault("Rule")
                    withContext(Dispatchers.Main) {
                        design.setMode(mode)
                    }
                }
                delay(3000)
            }
        }
    }

    private suspend fun handleRequest(request: MainDesign.Request) {
        when (request) {
            MainDesign.Request.ToggleStatus -> {
                if (backend.serviceState.value == ServiceState.RUNNING) {
                    backend.stopVpn()
                } else {
                    when (val result = backend.prepareStartVpn(this)) {
                        is StartVpnResult.PermissionNotRequired -> backend.startVpnAfterPermission()
                        is StartVpnResult.Prepared -> vpnPermissionLauncher.launch(result.intent)
                        is StartVpnResult.Error -> design.showToast(result.message)
                    }
                }
            }
            MainDesign.Request.OpenProxy -> {
                // TODO: Open MetaProxyActivity
            }
            MainDesign.Request.OpenProfiles -> {
                // TODO: Open MetaProfilesActivity
            }
            MainDesign.Request.OpenLogs -> {
                // TODO: Open MetaLogsActivity
            }
            MainDesign.Request.OpenSettings -> {
                // TODO: Open MetaSettingsActivity
            }
            MainDesign.Request.OpenHelp -> {
                design.showToast("Help not yet available")
            }
            MainDesign.Request.OpenAbout -> {
                design.showToast("AChimera ${Global.application.getString(R.string.app_ver)}")
            }
        }
    }
}
