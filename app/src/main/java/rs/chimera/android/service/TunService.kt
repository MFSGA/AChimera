package rs.chimera.android.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.AssetManager
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rs.chimera.android.Global
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.BackendRuntimeState
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.util.NotificationHelper
import rs.chimera.android.util.PrivacySafeLog
import rs.chimera.android.ffi.initClash
import rs.chimera.android.ffi.shutdownClash
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TunService : VpnService(), VpnRuntimeControl {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunFd: Int? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleGate = VpnLifecycleGate()
    private val runtimeMutex = Mutex()
    private val backend by lazy { BackendProvider.provide() }
    private val desiredStateStore by lazy { VpnDesiredStateStore(this) }
    private val networkResetMutex = Mutex()
    private val networkResetGeneration = NetworkResetGeneration()
    private var networkCoordinator: UnderlyingNetworkCoordinator? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        restoreRuntimeRequestIfEligible()
        if (!VpnRuntimeRegistry.shouldRun) {
            Log.i(TAG, "Discarding cancelled VPN start request")
            appendRuntimeLog("discarded cancelled vpn start request")
            ensureForegroundService()
            cleanup()
            stopSelf()
            return START_NOT_STICKY
        }

        val shouldStart = lifecycleGate.requestStart()
        if (!shouldStart) {
            Log.i(TAG, "Ignoring duplicate VPN start request")
            appendRuntimeLog("ignored duplicate vpn start request")
            return START_STICKY
        }

        Log.i(TAG, "onStartCommand")
        appendRuntimeLog("service onStartCommand")
        ensureForegroundService()
        if (!VpnRuntimeRegistry.register(this)) {
            appendRuntimeLog("vpn start registration cancelled")
            cleanup()
            stopSelf()
            return START_NOT_STICKY
        }
        publishVpnSystemStatus(serviceActive = true)
        BackendRuntimeState.updateServiceState(ServiceState.STARTING)

        val job = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                runtimeMutex.withLock { runVpn() }
            } catch (error: CancellationException) {
                appendRuntimeLog("service startup cancelled")
                throw error
            } catch (error: Exception) {
                recordDesiredStop(VpnDesiredStateReason.START_FAILED)
                PrivacySafeLog.error(TAG, "Error in runVpn", error)
                appendRuntimeLog("service runVpn failed", error)
                val detail =
                    error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                val cleanedUp = cleanup(
                    finalState = ServiceState.ERROR,
                    errorMessage = detail,
                )
                if (cleanedUp) {
                    NotificationHelper.notifyFailed(this@TunService, detail)
                    stopSelf()
                }
            } finally {
                currentCoroutineContext()[Job]?.let(lifecycleGate::clearStartup)
            }
        }
        val shouldLaunch = lifecycleGate.registerStartup(job)
        if (shouldLaunch) {
            job.start()
        } else {
            job.cancel()
        }

        return START_STICKY
    }

    override fun onRevoke() {
        recordDesiredStop(VpnDesiredStateReason.PERMISSION_REVOKED)
        cancelStartup()
        cleanup()
        super.onRevoke()
    }

    override fun onDestroy() {
        VpnRuntimeRegistry.requestStop()
        cancelStartup()
        cleanup()
        super.onDestroy()
    }

    private fun restoreRuntimeRequestIfEligible() {
        if (VpnRuntimeRegistry.shouldRun) return

        when (
            VpnRecoveryPolicy.decide(
                snapshot = desiredStateStore.snapshot(),
                profileAvailable = Global.restoreProfilePath().isNotBlank(),
                permissionGranted = VpnService.prepare(this) == null,
            )
        ) {
            VpnRecoveryDecision.RESTORE -> {
                VpnRuntimeRegistry.requestStart()
                appendRuntimeLog("restored persisted vpn run request")
            }

            VpnRecoveryDecision.BLOCK_MISSING_PROFILE -> {
                recordDesiredStop(VpnDesiredStateReason.RECOVERY_MISSING_PROFILE)
                appendRuntimeLog("vpn recovery blocked: missing profile")
            }

            VpnRecoveryDecision.BLOCK_PERMISSION_REQUIRED -> {
                recordDesiredStop(VpnDesiredStateReason.RECOVERY_PERMISSION_REQUIRED)
                appendRuntimeLog("vpn recovery blocked: permission required")
            }

            VpnRecoveryDecision.DISCARD_STOPPED -> Unit
        }
    }

    private fun recordDesiredStop(reason: VpnDesiredStateReason) {
        runCatching { desiredStateStore.markStopped(reason) }
            .onFailure { error ->
                PrivacySafeLog.warning(TAG, "Failed to persist desired VPN stop state", error)
                appendRuntimeLog("failed to persist desired vpn stop state", error)
            }
        VpnRuntimeRegistry.requestStop()
    }

    private suspend fun runVpn() {
        currentCoroutineContext().ensureActive()
        val profilePath = resolveProfilePath()
        val settings = TunServiceSettingsLoader.load(this)
        currentCoroutineContext().ensureActive()
        appendRuntimeLog(
            "service preparing vpn for profile: ${RuntimeLogSanitizer.profileLabel(profilePath)}",
        )
        val interfaceFd = buildTunnel(settings)
        if (interfaceFd == null) {
            error("Failed to establish VPN interface")
        }
        vpnInterface = interfaceFd
        startUnderlyingNetworkTracking()
        currentCoroutineContext().ensureActive()
        val currentTunFd = duplicateTunFdForRust(interfaceFd)
        tunFd = currentTunFd
        if (currentTunFd <= 0) {
            closeDetachedTunFd(currentTunFd)
            tunFd = null
            error("Invalid tun fd: $currentTunFd")
        }

        currentCoroutineContext().ensureActive()
        copyRuntimeAssetsIfAvailable(Global.application.assets, Global.application.cacheDir)
        currentCoroutineContext().ensureActive()

        val startResult =
            initClash(
                configPath = profilePath,
                workDir = Global.application.cacheDir.absolutePath,
                over = TunServiceSettingsLoader.createProfileOverride(
                    currentTunFd = currentTunFd,
                    settings = settings,
                    logFilePath = "${Global.application.cacheDir}/chimera-rs.log",
                ),
            )
        currentCoroutineContext().ensureActive()
        if (startResult.isFailure) {
            closeDetachedTunFd(currentTunFd)
            tunFd = null
            throw startResult.exceptionOrNull()
                ?: IllegalStateException("Failed to initialize Rust core")
        }

        val finalProfile = startResult.getOrThrow()
        currentCoroutineContext().ensureActive()
        Global.proxyPort = finalProfile.mixedPort
        appendRuntimeLog("service rust core started on mixed-port=${finalProfile.mixedPort}")
        NotificationHelper.notifyRunning(this)
        BackendRuntimeState.updateServiceState(ServiceState.RUNNING)
    }

    private fun buildTunnel(settings: TunServiceSettings): ParcelFileDescriptor? {
        val builder = Builder()
        builder.setSession("ClashRS VPNService")
        builder.addAddress(TUN_GATEWAY_V4, TUN_PREFIX_V4)
        builder.addRoute("0.0.0.0", 0)
        builder.addDnsServer(TUN_DNS_V4)
        if (settings.ipv6) {
            builder.addAddress(TUN_GATEWAY_V6, TUN_PREFIX_V6)
            builder.addRoute("::", 0)
            builder.addDnsServer(TUN_DNS_V6)
        }
        applyAppFilter(builder, settings)
        builder.allowBypass()
        return builder.establish()
    }

    private fun startUnderlyingNetworkTracking() {
        networkResetGeneration.invalidate()
        networkCoordinator?.stop()
        networkCoordinator =
            UnderlyingNetworkCoordinator(
                connectivityManager = getSystemService(ConnectivityManager::class.java),
                applyNetworks = ::setUnderlyingNetworks,
                onPrimaryNetworkChanged = ::handleUnderlyingNetworkChange,
            ).also(UnderlyingNetworkCoordinator::start)
    }

    private fun handleUnderlyingNetworkChange() {
        val generation = networkResetGeneration.next()
        appendRuntimeLog("underlying network changed")
        serviceScope.launch {
            if (BackendRuntimeState.serviceState.value != ServiceState.RUNNING) return@launch
            networkResetMutex.withLock {
                if (BackendRuntimeState.serviceState.value != ServiceState.RUNNING) return@withLock
                if (!networkResetGeneration.isLatest(generation)) return@withLock
                try {
                    backend.resetNetwork()
                    appendRuntimeLog("core network state reset after handoff")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    PrivacySafeLog.warning(TAG, "Failed to reset core after network handoff", error)
                    appendRuntimeLog("failed to reset core after network handoff", error)
                }
            }
        }
    }

    private fun duplicateTunFdForRust(interfaceFd: ParcelFileDescriptor): Int =
        ParcelFileDescriptor
            .dup(interfaceFd.fileDescriptor)
            .detachFd()

    private fun closeDetachedTunFd(fd: Int) {
        if (fd < 0) return
        runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
            .onFailure { error ->
                PrivacySafeLog.warning(TAG, "Failed to close detached TUN fd", error, debugDetail = "fd=$fd")
                appendRuntimeLog("failed to close detached tun fd: $fd", error)
            }
    }

    private fun resolveProfilePath(): String {
        val path =
            if (Global.profilePath.isBlank()) {
                Global.restoreProfilePath()
            } else {
                Global.profilePath
            }.trim()

        if (path.isEmpty()) {
            throw IllegalStateException(getString(rs.chimera.android.R.string.service_profile_required))
        }

        val configFile = File(path)
        if (!configFile.exists() || !configFile.isFile) {
            throw IllegalStateException("Profile file not found: $path")
        }

        return path
    }

    private fun applyAppFilter(
        builder: Builder,
        settings: TunServiceSettings,
    ) {
        when (settings.appFilterMode) {
            "ALLOWED" -> {
                settings.allowedApps.forEach { appPackageName ->
                    runCatching { builder.addAllowedApplication(appPackageName) }
                        .onFailure { error ->
                            PrivacySafeLog.warning(TAG, "Failed to add allowed app", error, debugDetail = appPackageName)
                        }
                }
            }
            "DISALLOWED" -> {
                addDisallowedApplicationSafely(builder, packageName)
                settings.disallowedApps.forEach { appPackageName ->
                    addDisallowedApplicationSafely(builder, appPackageName)
                }
            }
            else -> addDisallowedApplicationSafely(builder, packageName)
        }
    }

    private fun addDisallowedApplicationSafely(
        builder: Builder,
        appPackageName: String,
    ) {
        runCatching { builder.addDisallowedApplication(appPackageName) }
            .onFailure { error ->
                PrivacySafeLog.warning(TAG, "Failed to add disallowed app", error, debugDetail = appPackageName)
                appendRuntimeLog("failed to add disallowed app: $appPackageName", error)
            }
    }

    private fun copyRuntimeAssetsIfAvailable(
        assets: AssetManager,
        cacheDir: File,
    ) {
        listOf("Country.mmdb", "geosite.dat").forEach { name ->
            runCatching {
                assets.open("clash-res/$name").use { input ->
                    val output = File(cacheDir, name)
                    output.deleteOnExit()
                    if (!output.exists()) {
                        output.createNewFile()
                    }
                    output.outputStream().use { stream ->
                        input.copyTo(stream)
                    }
                }
            }.onFailure { error ->
                PrivacySafeLog.warning(TAG, "Runtime asset unavailable", error, debugDetail = name)
                appendRuntimeLog("runtime asset unavailable: $name", error)
            }
        }
    }

    private fun publishVpnSystemStatus(serviceActive: Boolean) {
        val previous = BackendRuntimeState.vpnSystemStatus.value
        val status = if (serviceActive) {
            VpnSystemStatusPolicy.observed(
                apiLevel = Build.VERSION.SDK_INT,
                serviceActive = true,
                alwaysOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAlwaysOn,
                lockdown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isLockdownEnabled,
                observedAt = System.currentTimeMillis(),
            )
        } else {
            VpnSystemStatusPolicy.inactive(previous)
        }
        BackendRuntimeState.updateVpnSystemStatus(status)
        if (serviceActive) {
            appendRuntimeLog(
                "vpn system status observed: always_on=${status.alwaysOn} lockdown=${status.lockdown}",
            )
        }
    }

    private fun ensureForegroundService() {
        NotificationHelper.ensureChannel(this)
        val notification = NotificationHelper.buildStartingNotification(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private fun cancelStartup(): Job? =
        lifecycleGate.cancelStartup()

    private fun cleanup(
        finalState: ServiceState = ServiceState.STOPPED,
        stopCore: Boolean = true,
        errorMessage: String? = null,
    ): Boolean {
        if (!lifecycleGate.beginCleanup()) return false

        if (errorMessage != null) {
            BackendRuntimeState.updateServiceError(errorMessage)
        }
        releaseRuntime(stopCore = stopCore, removeForeground = true)
        publishVpnSystemStatus(serviceActive = false)
        VpnRuntimeRegistry.unregister(this)
        BackendRuntimeState.updateServiceState(finalState)
        appendRuntimeLog("service cleanup complete")
        return true
    }

    private fun releaseRuntime(
        stopCore: Boolean,
        removeForeground: Boolean,
    ) {
        networkResetGeneration.invalidate()
        networkCoordinator?.stop()
        networkCoordinator = null
        if (stopCore) {
            shutdownClash().exceptionOrNull()?.let { error ->
                PrivacySafeLog.warning(TAG, "Failed to stop Rust core cleanly", error)
                appendRuntimeLog("failed to stop rust core cleanly", error)
            }
        }
        if (removeForeground) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            }.onFailure { error ->
                PrivacySafeLog.warning(TAG, "Failed to stop foreground service", error)
                appendRuntimeLog("failed to stop foreground service", error)
            }
        }
        runCatching { vpnInterface?.close() }
            .onFailure { error ->
                PrivacySafeLog.warning(TAG, "Failed to close VPN interface", error)
                appendRuntimeLog("failed to close vpn interface", error)
            }
        vpnInterface = null
        tunFd = null
        Global.proxyPort = null
    }

    override suspend fun restartVpn() {
        check(lifecycleGate.canRunRuntime()) { "VPN service is stopping" }

        try {
            runtimeMutex.withLock {
                check(lifecycleGate.canRunRuntime()) { "VPN service is stopping" }
                BackendRuntimeState.updateServiceState(ServiceState.STOPPING)
                appendRuntimeLog("service runtime reload requested")
                ensureForegroundService()
                releaseRuntime(stopCore = true, removeForeground = false)

                if (!lifecycleGate.canRunRuntime()) {
                    throw CancellationException("VPN runtime reload cancelled")
                }

                BackendRuntimeState.updateServiceState(ServiceState.STARTING)
                runVpn()
                appendRuntimeLog("service runtime reload complete")
            }
        } catch (error: CancellationException) {
            appendRuntimeLog("service runtime reload cancelled")
            throw error
        } catch (error: Exception) {
            recordDesiredStop(VpnDesiredStateReason.RUNTIME_FAILED)
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
            runtimeMutex.withLock {
                if (cleanup(finalState = ServiceState.ERROR, errorMessage = detail)) {
                    NotificationHelper.notifyFailed(this@TunService, detail)
                    stopSelf()
                }
            }
            throw error
        }
    }

    override fun onCoreStopped(message: String) {
        recordDesiredStop(VpnDesiredStateReason.CORE_EXITED)
        serviceScope.launch {
            runtimeMutex.withLock {
                if (!lifecycleGate.canHandleUnexpectedCoreStop()) return@withLock

                val cleanedUp = cleanup(
                    finalState = ServiceState.ERROR,
                    stopCore = false,
                    errorMessage = message,
                )
                if (!cleanedUp) return@withLock

                PrivacySafeLog.errorDetail(TAG, "Rust core stopped unexpectedly", message)
                appendRuntimeLog("rust core stopped unexpectedly: $message")
                NotificationHelper.notifyFailed(this@TunService, message)
                stopSelf()
            }
        }
    }

    override suspend fun stopVpn() {
        recordDesiredStop(VpnDesiredStateReason.USER_STOP)
        val stopRequest = lifecycleGate.requestStop()
        if (!stopRequest.accepted) return

        stopRequest.startupJob?.join()
        runtimeMutex.withLock {
            if (cleanup()) {
                stopSelf()
            }
        }
    }

    override fun protectSocket(fd: Int): Boolean = protect(fd)

    private companion object {
        const val TAG = "ChimeraTunService"
        const val TUN_GATEWAY_V4 = "10.0.0.1"
        const val TUN_DNS_V4 = "10.0.0.2"
        const val TUN_PREFIX_V4 = 30
        const val TUN_GATEWAY_V6 = "fdfe:dcba:9876::1"
        const val TUN_DNS_V6 = "fdfe:dcba:9876::2"
        const val TUN_PREFIX_V6 = 126
    }

    private fun appendRuntimeLog(
        message: String,
        error: Throwable? = null,
    ) {
        val file = Global.runtimeLogFile()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val line =
            buildString {
                append('[')
                append(timestamp)
                append("] ")
                append(message)
                if (error != null) {
                    append(": ")
                    append(error.message ?: error.javaClass.simpleName)
                }
            }
        runCatching {
            RuntimeLogStore.shared.append(file, line)
        }
    }
}
