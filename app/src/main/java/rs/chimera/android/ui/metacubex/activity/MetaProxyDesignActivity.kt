package rs.chimera.android.ui.metacubex.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.ui.metacubex.design.ProxyDesign
import rs.chimera.android.util.runCatchingPreservingCancellation

class MetaProxyDesignActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: ProxyDesign
    private var initialLoadComplete = false
    private var refreshing = false
    private var selecting = false
    private var testing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        design = ProxyDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                handleRequest(request)
            }
        }

        observeProxyGroups()
    }

    private fun observeProxyGroups() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    backend.serviceState.collectLatest { state ->
                        when (state) {
                            ServiceState.RUNNING -> {
                                initialLoadComplete = false
                                design.showLoading()
                                refreshGroups()
                            }
                            ServiceState.STARTING -> {
                                initialLoadComplete = false
                                design.showWaiting(getString(R.string.cmfa_service_starting))
                            }
                            ServiceState.STOPPING -> {
                                initialLoadComplete = false
                                design.showWaiting(getString(R.string.cmfa_service_stopping))
                            }
                            ServiceState.STOPPED -> {
                                initialLoadComplete = false
                                design.showNotRunning()
                            }
                            ServiceState.ERROR -> {
                                initialLoadComplete = false
                                design.showError(
                                    backend.runtimeError.value?.message
                                        ?: getString(R.string.cmfa_service_retry),
                                    showRetry = false,
                                )
                            }
                        }
                    }
                }

                launch {
                    backend.proxyGroups.collect { snapshots ->
                        if (
                            backend.serviceState.value == ServiceState.RUNNING &&
                            initialLoadComplete &&
                            !refreshing &&
                            !selecting &&
                            !testing
                        ) {
                            design.setGroups(snapshots)
                        }
                    }
                }

                launch {
                    backend.runtimeError.collect { error ->
                        if (
                            error?.source == BackendRuntimeErrorSource.PROXY_GROUPS &&
                            backend.serviceState.value == ServiceState.RUNNING &&
                            initialLoadComplete &&
                            !refreshing
                        ) {
                            design.showError(error.message)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleRequest(request: ProxyDesign.Request) {
        when (request) {
            is ProxyDesign.Request.SelectProxy -> selectProxy(request)
            is ProxyDesign.Request.DelayTest -> testGroupDelay(request)
            is ProxyDesign.Request.SwitchMode -> switchMode(request)
            ProxyDesign.Request.Refresh -> refreshGroups(showLoading = true)
            ProxyDesign.Request.NavigateBack -> finish()
        }
    }

    private suspend fun refreshGroups(showLoading: Boolean = false) {
        if (backend.serviceState.value != ServiceState.RUNNING) {
            design.showNotRunning()
            return
        }
        if (refreshing) return

        refreshing = true
        if (showLoading) design.showLoading()
        try {
            runCatchingPreservingCancellation {
                withContext(Dispatchers.IO) { backend.listProxyGroups() }
            }.onSuccess { snapshots ->
                initialLoadComplete = true
                val proxyError = backend.runtimeError.value
                    ?.takeIf { it.source == BackendRuntimeErrorSource.PROXY_GROUPS }
                if (snapshots.isEmpty() && proxyError != null) {
                    design.showError(proxyError.message)
                } else {
                    design.setGroups(snapshots)
                }
            }.onFailure { error ->
                initialLoadComplete = true
                design.showError(error.message ?: getString(R.string.proxy_refresh_failed))
            }
        } finally {
            refreshing = false
        }
    }

    private suspend fun selectProxy(request: ProxyDesign.Request.SelectProxy) {
        if (selecting || testing) return

        selecting = true
        design.setSelecting(true)
        try {
            runCatchingPreservingCancellation {
                withContext(Dispatchers.IO) {
                    backend.selectProxy(request.groupName, request.proxyName)
                    backend.listProxyGroups()
                }
            }.onSuccess { snapshots ->
                initialLoadComplete = true
                design.setGroups(snapshots)
                val selected = snapshots
                    .firstOrNull { it.name == request.groupName }
                    ?.selected == request.proxyName
                val message = if (selected) {
                    getString(R.string.proxy_select_success, request.proxyName)
                } else {
                    getString(R.string.proxy_select_not_applied, request.proxyName)
                }
                design.showToast(message)
            }.onFailure { error ->
                design.showToast(
                    getString(
                        R.string.proxy_select_failed,
                        error.message ?: getString(R.string.profile_unknown_error),
                    ),
                )
            }
        } finally {
            selecting = false
            design.setSelecting(false)
        }
    }

    private suspend fun switchMode(request: ProxyDesign.Request.SwitchMode) {
        if (selecting || testing || refreshing) return
        if (backend.serviceState.value != ServiceState.RUNNING) {
            design.showNotRunning()
            return
        }

        selecting = true
        design.setSelecting(true)
        try {
            val result = runCatchingPreservingCancellation {
                withContext(Dispatchers.IO) { backend.setMode(request.mode) }
            }
            result.onSuccess {
                design.setMode(request.mode)
                refreshGroups()
            }.onFailure { error ->
                design.showToast(
                    getString(
                        R.string.proxy_mode_switch_failed,
                        error.message ?: getString(R.string.profile_unknown_error),
                    ),
                )
            }
        } finally {
            selecting = false
            design.setSelecting(false)
        }
    }

    private suspend fun testGroupDelay(request: ProxyDesign.Request.DelayTest) {
        if (testing || selecting || request.proxyNames.isEmpty()) return

        testing = true
        var failures = 0
        try {
            request.proxyNames.forEachIndexed { index, proxyName ->
                design.setDelayTestProgress(index + 1, request.proxyNames.size)
                val result = runCatchingPreservingCancellation {
                    withContext(Dispatchers.IO) {
                        backend.testProxyDelay(proxyName)
                    }
                }
                if (result.isFailure) failures++
            }

            val message = if (failures == 0) {
                getString(R.string.proxy_delay_complete, request.groupName)
            } else {
                getString(
                    R.string.proxy_delay_complete_with_failures,
                    request.groupName,
                    failures,
                )
            }
            design.showToast(message)
            refreshGroups()
        } finally {
            testing = false
            design.finishDelayTest()
        }
    }
}
