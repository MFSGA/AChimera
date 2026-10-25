package rs.chimera.android.ui.metacubex.activity

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
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
    private var pendingProxySelections = 0
    private var switchingMode = false
    private val selecting: Boolean
        get() = pendingProxySelections > 0 || switchingMode
    private val proxyDelayTestTracker = ProxyDelayTestTracker()
    private val testing: Boolean
        get() = proxyDelayTestTracker.isTesting
    private val proxyGroupRefreshGate = ProxyGroupRefreshGate()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        design = ProxyDesign(this)
        design.onProxySelectionQueueChanged = { enqueued ->
            if (enqueued) {
                proxyGroupRefreshGate.selectionStarted()
                pendingProxySelections++
            } else {
                pendingProxySelections = (pendingProxySelections - 1).coerceAtLeast(0)
                finishProxyMutationsIfIdle()
            }
        }
        setContentView(design.root)

        lifecycleScope.launch {
            consumeProxyRequests(design.requests, ::handleRequest)
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
                            design.setGroups(snapshots, preserveCachedSelections = true)
                        }
                    }
                }

                launch {
                    backend.proxyModeOverride.collect(design::setModeOverride)
                }

                launch {
                    backend.runtimeError.collect { error ->
                        val runtimeError = error ?: return@collect
                        if (
                            shouldShowProxyGroupRuntimeError(
                                errorSource = runtimeError.source,
                                serviceState = backend.serviceState.value,
                                initialLoadComplete = initialLoadComplete,
                                selecting = selecting,
                                refreshing = refreshing,
                                testing = testing,
                            )
                        ) {
                            design.showError(runtimeError.message)
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleRequest(request: ProxyDesign.Request) {
        when (request) {
            is ProxyDesign.Request.SelectProxy -> selectProxy(request)
            is ProxyDesign.Request.DelayTest -> startGroupDelayTest(request)
            is ProxyDesign.Request.SwitchMode -> switchMode(request)
            ProxyDesign.Request.Refresh -> refreshGroups(showLoading = true)
            ProxyDesign.Request.NavigateBack -> finish()
        }
    }

    private fun startGroupDelayTest(request: ProxyDesign.Request.DelayTest) {
        if (request.proxyNames.isEmpty()) return
        if (!proxyDelayTestTracker.start(request.groupName, request.proxyNames.size)) return

        // CMFA handles URL tests in a child coroutine so selection requests keep flowing.
        design.setDelayTestProgress(request.groupName, 1, request.proxyNames.size)
        lifecycleScope.launch {
            testGroupDelay(request)
        }
    }

    private suspend fun refreshGroups(showLoading: Boolean = false) {
        if (backend.serviceState.value != ServiceState.RUNNING) {
            design.restoreConfirmedProxyGroups()
            design.showNotRunning()
            return
        }
        val refreshRevision = proxyGroupRefreshGate.requestRefresh(selecting, refreshing) ?: return
        refreshing = true
        if (showLoading) design.showLoading()
        try {
            runCatchingPreservingCancellation {
                withContext(Dispatchers.IO) { backend.listProxyGroups() }
            }.onSuccess { snapshots ->
                initialLoadComplete = true
                if (
                    proxyGroupRefreshGate.shouldApply(
                        refreshRevision,
                        selecting,
                        serviceRunning = backend.serviceState.value == ServiceState.RUNNING,
                    )
                ) {
                    val proxyError = backend.runtimeError.value
                        ?.takeIf { it.source == BackendRuntimeErrorSource.PROXY_GROUPS }
                    if (snapshots.isEmpty() && proxyError != null) {
                        design.showError(proxyError.message)
                    } else {
                        design.setGroups(snapshots)
                    }
                }
            }.onFailure { error ->
                initialLoadComplete = true
                if (
                    proxyGroupRefreshGate.shouldApply(
                        refreshRevision,
                        selecting,
                        serviceRunning = backend.serviceState.value == ServiceState.RUNNING,
                    )
                ) {
                    design.restoreConfirmedProxyGroups()
                    design.showError(error.message ?: getString(R.string.proxy_refresh_failed))
                }
            }
        } finally {
            refreshing = false
            refreshPendingGroupsIfNeeded()
        }
    }

    private fun refreshPendingGroupsIfNeeded() {
        if (backend.serviceState.value != ServiceState.RUNNING) return
        if (!proxyGroupRefreshGate.takePendingRefresh(selecting, refreshing)) return
        lifecycleScope.launch {
            refreshGroups()
        }
    }

    private suspend fun selectProxy(request: ProxyDesign.Request.SelectProxy) {
        if (pendingProxySelections == 0) {
            proxyGroupRefreshGate.selectionStarted()
            pendingProxySelections = 1
        }
        try {
            runProxySelection(
                selectionProjected = request.selectionProjected,
                select = {
                    withContext(Dispatchers.IO) {
                        backend.selectProxy(request.groupName, request.proxyName)
                    }
                },
                confirmSelection = {
                    design.confirmProxySelection(
                        request.selectionToken,
                        request.groupName,
                        request.proxyName,
                    )
                },
                refresh = { refreshGroups() },
                onFailure = { error ->
                    design.rejectProxySelection(request.selectionToken)
                    design.showToast(
                        getString(
                            R.string.proxy_select_failed,
                            error.message ?: getString(R.string.profile_unknown_error),
                        ),
                    )
                },
            )
        } finally {
            pendingProxySelections = (pendingProxySelections - 1).coerceAtLeast(0)
            finishProxyMutationsIfIdle()
        }
    }

    private fun finishProxyMutationsIfIdle() {
        if (selecting) return
        proxyGroupRefreshGate.selectionFinished()
        refreshPendingGroupsIfNeeded()
    }

    private suspend fun switchMode(request: ProxyDesign.Request.SwitchMode) {
        if (!canStartProxyModeSwitch(switchingMode)) return
        if (backend.serviceState.value != ServiceState.RUNNING) {
            design.showNotRunning()
            return
        }

        design.showModeSwitchTips()
        proxyGroupRefreshGate.selectionStarted()
        switchingMode = true
        try {
            runProxyModeSwitch(
                mode = request.mode,
                applyMode = { mode ->
                    withContext(Dispatchers.IO) { backend.setMode(mode) }
                },
                currentMode = { backend.proxyModeOverride.value },
                setDisplayedMode = design::setModeOverride,
                refresh = { refreshGroups() },
                onFailure = { error ->
                    design.showToast(
                        getString(
                            R.string.proxy_mode_switch_failed,
                            error.message ?: getString(R.string.profile_unknown_error),
                        ),
                    )
                },
            )
        } finally {
            switchingMode = false
            finishProxyMutationsIfIdle()
        }
    }

    private suspend fun testGroupDelay(request: ProxyDesign.Request.DelayTest) {
        var failures = 0
        try {
            request.proxyNames.forEachIndexed { index, proxyName ->
                val current = index + 1
                proxyDelayTestTracker.update(request.groupName, current, request.proxyNames.size)
                design.setDelayTestProgress(request.groupName, current, request.proxyNames.size)
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
                resources.getQuantityString(
                    R.plurals.proxy_delay_complete_with_failures,
                    failures,
                    request.groupName,
                    failures,
                )
            }
            design.showToast(message)
            refreshGroups()
        } finally {
            proxyDelayTestTracker.finish(request.groupName)
            design.finishDelayTest(request.groupName)
            refreshPendingGroupsIfNeeded()
        }
    }
}

internal fun shouldShowProxyGroupRuntimeError(
    errorSource: BackendRuntimeErrorSource?,
    serviceState: ServiceState,
    initialLoadComplete: Boolean,
    selecting: Boolean,
    refreshing: Boolean,
    testing: Boolean,
): Boolean =
    errorSource == BackendRuntimeErrorSource.PROXY_GROUPS &&
        serviceState == ServiceState.RUNNING &&
        initialLoadComplete &&
        !selecting &&
        !refreshing &&
        !testing
