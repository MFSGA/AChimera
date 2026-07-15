package rs.chimera.android.ui.metacubex.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.Global
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.StartVpnResult
import rs.chimera.android.ui.metacubex.design.MainDesign
import rs.chimera.android.ui.navigation.DefaultAppUiRouter
import rs.chimera.android.formatSize

class MetaMainActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: MainDesign

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            lifecycleScope.launch(Dispatchers.Default) {
                backend.startVpnAfterPermission()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        design = MainDesign(this)
        setContentView(design.root)

        lifecycleScope.launch(Dispatchers.Default) {
            for (request in design.requests) {
                handleRequest(request)
            }
        }

        observeBackend()
    }

    private fun observeBackend() {
        lifecycleScope.launch(Dispatchers.Default) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    backend.serviceState.collect { state ->
                        val running = state == ServiceState.RUNNING
                        withContext(Dispatchers.Main) {
                            design.setClashRunning(running)
                        }
                    }
                }

                launch {
                    backend.activeProfile.collect { profile ->
                        withContext(Dispatchers.Main) {
                            design.setProfileName(profile?.name)
                        }
                    }
                }

                launch {
                    backend.traffic.collect { traffic ->
                        val total = formatSize(traffic.downloadTotal + traffic.uploadTotal)
                        withContext(Dispatchers.Main) {
                            design.setForwarded(total)
                        }
                    }
                }

                launch {
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

                launch {
                    backend.runtimeError.collect { error ->
                        error?.let {
                            design.showToast(it.message)
                        }
                    }
                }
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
                        is StartVpnResult.Prepared -> withContext(Dispatchers.Main) {
                            vpnPermissionLauncher.launch(result.intent)
                        }
                        is StartVpnResult.Error -> design.showToast(result.message)
                    }
                }
            }
            MainDesign.Request.OpenProxy -> {
                startActivity(Intent(this, MetaProxyDesignActivity::class.java))
            }
            MainDesign.Request.OpenProfiles -> {
                startActivity(Intent(this, MetaProfilesDesignActivity::class.java))
            }
            MainDesign.Request.OpenLogs -> {
                startActivity(Intent(this, MetaLogsDesignActivity::class.java))
            }
            MainDesign.Request.OpenSettings -> {
                startActivity(Intent(this, MetaSettingsActivity::class.java))
            }
            MainDesign.Request.OpenWatfaq -> {
                withContext(Dispatchers.Main) {
                    DefaultAppUiRouter.openWatfaq(this@MetaMainActivity)
                    finish()
                }
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
