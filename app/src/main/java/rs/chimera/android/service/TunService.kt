package rs.chimera.android.service

import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.ProxyInfo
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rs.chimera.android.Global
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.ProfileRuntimeDirectoryPolicy
import rs.chimera.android.backend.BackendRuntimeState
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.util.NotificationHelper
import rs.chimera.android.util.PrivacySafeLog
import rs.chimera.android.util.runCatchingRecoverable
import rs.chimera.android.util.sanitizeUserVisibleErrorText
import rs.chimera.android.util.toUserVisibleMessage
import rs.chimera.android.ffi.ProfileProxyEndpoint
import rs.chimera.android.ffi.initClash
import rs.chimera.android.ffi.profileProxyEndpoint
import rs.chimera.android.ffi.shutdownClash
import java.util.concurrent.atomic.AtomicBoolean

class TunService : VpnService(), VpnRuntimeControl {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunFd: Int? = null
    private var trafficNotificationJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycleGate = VpnLifecycleGate()
    private val runtimeMutex = Mutex()
    private val backend by lazy { BackendProvider.provide() }
    private val desiredStateStore by lazy { VpnDesiredStateStore(this) }
    private val networkResetMutex = Mutex()
    private val networkResetGeneration = NetworkResetGeneration()
    private val lockdownRouteRestartScheduled = AtomicBoolean(false)

    @Volatile
    private var systemAlwaysOnStartObserved = false

    private var networkCoordinator: UnderlyingNetworkCoordinator? = null
    private val cleanupCoordinator by lazy {
        VpnRuntimeCleanupCoordinator(
            lifecycleGate = lifecycleGate,
            runtimeControl = this,
            releaseRuntime = ::releaseRuntime,
            publishVpnInactive = {
                systemAlwaysOnStartObserved = false
                publishVpnSystemStatus(serviceActive = false, routesSystemTraffic = false)
            },
            formatReleaseFailure = { failure ->
                failure.toUserVisibleMessage(
                    this,
                    rs.chimera.android.R.string.profile_unknown_error,
                )
            },
            logCleanupComplete = { appendRuntimeLog("service cleanup complete") },
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val systemAlwaysOnStart = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            intent?.action == VpnService.SERVICE_INTERFACE
        systemAlwaysOnStartObserved =
            systemAlwaysOnStart ||
                (
                    BackendRuntimeState.serviceState.value == ServiceState.STARTING &&
                        systemAlwaysOnStartObserved
                )
        val systemModes = readVpnSystemModes(systemAlwaysOnStartObserved)
        restoreRuntimeRequestIfEligible(systemModes.alwaysOn)
        if (!VpnRuntimeRegistry.shouldRun) {
            Log.i(TAG, "Discarding cancelled VPN start request")
            appendRuntimeLog("discarded cancelled vpn start request")
            ensureForegroundService()
            requestLifecycleCleanup(stopService = true)
            return START_NOT_STICKY
        }

        val effectiveSettings = effectiveTunSettings(
            settings = TunServiceSettingsLoader.load(this),
            systemLockdownEnabled = systemModes.lockdown,
        )
        val shouldStart = lifecycleGate.requestStart()
        if (!shouldStart) {
            val currentServiceState = BackendRuntimeState.serviceState.value
            val serviceActive = currentServiceState == ServiceState.STARTING ||
                currentServiceState == ServiceState.RUNNING
            publishVpnSystemStatus(
                serviceActive = serviceActive,
                routesSystemTraffic = effectiveSettings.routeSystemTraffic,
            )
            val shouldRestoreTunnel = VpnSystemStatusPolicy.shouldRestartForSystemTrafficRouting(
                serviceActive = serviceActive,
                tunnelActive = vpnInterface != null,
                shouldRouteSystemTraffic = effectiveSettings.routeSystemTraffic,
            )
            if (shouldRestoreTunnel && lockdownRouteRestartScheduled.compareAndSet(false, true)) {
                appendRuntimeLog("Android Lockdown requires restoring the VPN traffic route")
                serviceScope.launch {
                    try {
                        restartVpnForSystemRouting()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        appendRuntimeLog("failed to restore vpn route for Android Lockdown", error)
                    } finally {
                        lockdownRouteRestartScheduled.set(false)
                    }
                }
            } else {
                Log.i(TAG, "Ignoring duplicate VPN start request")
                appendRuntimeLog("ignored duplicate vpn start request")
            }
            return START_STICKY
        }

        Log.i(TAG, "onStartCommand")
        appendRuntimeLog("service onStartCommand")
        ensureForegroundService()
        if (!VpnRuntimeRegistry.register(this)) {
            appendRuntimeLog("vpn start registration cancelled")
            requestLifecycleCleanup(stopService = true)
            return START_NOT_STICKY
        }
        publishVpnSystemStatus(
            serviceActive = true,
            routesSystemTraffic = effectiveSettings.routeSystemTraffic,
        )
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
                val detail = error.toUserVisibleMessage(
                    this@TunService,
                    rs.chimera.android.R.string.profile_unknown_error,
                )
                val cleanupOutcome = cleanup(
                    finalState = ServiceState.ERROR,
                    errorMessage = detail,
                )
                cleanupOutcome.releaseFailure?.let(error::addSuppressed)
                if (cleanupOutcome.performed) {
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
        requestLifecycleCleanup()
        super.onRevoke()
    }

    override fun onDestroy() {
        VpnRuntimeRegistry.requestStop()
        requestLifecycleCleanup()
        super.onDestroy()
    }

    private fun restoreRuntimeRequestIfEligible(systemAlwaysOnEnabled: Boolean) {
        if (VpnRuntimeRegistry.shouldRun) return

        val settings = effectiveTunSettings(TunServiceSettingsLoader.load(this))
        val desiredState = desiredStateStore.snapshot()

        when (
            VpnRecoveryPolicy.decide(
                snapshot = desiredState,
                profileAvailable = Global.restoreProfilePath().isNotBlank(),
                permissionGranted =
                    !VpnPermissionPolicy.isRequired(settings.routeSystemTraffic) ||
                        VpnService.prepare(this) == null,
                systemAlwaysOnEnabled = systemAlwaysOnEnabled,
            )
        ) {
            VpnRecoveryDecision.RESTORE -> {
                VpnRuntimeRegistry.requestStart()
                appendRuntimeLog(
                    if (desiredState.shouldRun) {
                        "restored persisted vpn run request"
                    } else {
                        "accepted Android Always-on vpn start request"
                    },
                )
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
        runCatchingRecoverable { desiredStateStore.markStopped(reason) }
            .onFailure { error ->
                PrivacySafeLog.warning(TAG, "Failed to persist desired VPN stop state", error)
                appendRuntimeLog("failed to persist desired vpn stop state", error)
            }
        VpnRuntimeRegistry.requestStop()
    }

    private suspend fun runVpn() {
        backend.awaitReady()
        currentCoroutineContext().ensureActive()
        val profilePath = TunRuntimeFiles.resolveProfilePath(this)
        val configuredSettings = TunServiceSettingsLoader.load(this)
        val settings = effectiveTunSettings(configuredSettings)
        if (!configuredSettings.routeSystemTraffic && settings.routeSystemTraffic) {
            appendRuntimeLog("Android Lockdown requires routing system traffic through the VPN")
        }
        currentCoroutineContext().ensureActive()
        appendRuntimeLog(
            "service preparing vpn for profile: ${RuntimeLogSanitizer.profileLabel(profilePath)}",
        )
        val workDirectory = ProfileRuntimeDirectoryPolicy.directory(
            cacheDirectory = Global.application.cacheDir,
            profilePath = profilePath,
        )
        LegacyProviderDirectoryMigrator.prepare(
            legacyWorkDirectory = Global.application.cacheDir,
            profileWorkDirectory = workDirectory,
        )
        TunRuntimeFiles.copyRuntimeAssetsIfAvailable(
            Global.application.assets,
            workDirectory,
        )
        currentCoroutineContext().ensureActive()

        var currentTunFd: Int? = null
        val startResult = backend.withCoreProfileLoad(profilePath) { restoreSelections ->
            val systemProxyRequested =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                settings.routeSystemTraffic &&
                settings.systemProxy
            val systemProxyEndpoint = if (systemProxyRequested) {
                profileProxyEndpoint(profilePath, settings.mixedPort).getOrThrow()
            } else {
                null
            }
            if (systemProxyRequested && systemProxyEndpoint == null) {
                appendRuntimeLog("system proxy skipped: profile mixed-port is dynamic")
            }
            val interfaceFd = if (settings.routeSystemTraffic) {
                buildTunnel(settings, systemProxyEndpoint)
                    ?: error("Failed to establish VPN interface")
            } else {
                null
            }
            vpnInterface = interfaceFd
            startUnderlyingNetworkTracking(applyNetworksToVpn = interfaceFd != null)
            currentCoroutineContext().ensureActive()
            val resolvedTunFd = interfaceFd?.let(::duplicateTunFdForRust)
            currentTunFd = resolvedTunFd
            tunFd = resolvedTunFd
            if (resolvedTunFd != null && resolvedTunFd <= 0) {
                closeDetachedTunFd(resolvedTunFd)
                tunFd = null
                error("Invalid tun fd: $resolvedTunFd")
            }

            initClash(
                configPath = profilePath,
                workDir = workDirectory.absolutePath,
                over = TunServiceSettingsLoader.createProfileOverride(
                    currentTunFd = resolvedTunFd,
                    settings = settings,
                    logFilePath = "${Global.application.cacheDir}/chimera-rs.log",
                ),
            ).also { result ->
                if (result.isSuccess) restoreSelections()
            }
        }
        currentCoroutineContext().ensureActive()
        if (startResult.isFailure) {
            currentTunFd?.let(::closeDetachedTunFd)
            tunFd = null
            throw startResult.exceptionOrNull()
                ?: IllegalStateException("Failed to initialize Rust core")
        }

        val finalProfile = startResult.getOrThrow()
        currentCoroutineContext().ensureActive()
        Global.updateActiveCoreWorkDirectory(workDirectory.absolutePath)
        Global.proxyPort = finalProfile.mixedPort
        currentCoroutineContext().ensureActive()
        appendRuntimeLog("service rust core started on mixed-port=${finalProfile.mixedPort}")
        NotificationHelper.notifyRunning(this)
        BackendRuntimeState.updateServiceState(ServiceState.RUNNING)
        publishVpnSystemStatus(
            serviceActive = true,
            routesSystemTraffic = settings.routeSystemTraffic,
        )
        trafficNotificationJob = TrafficNotificationUpdater(this, backend).start(serviceScope)
    }

    private fun buildTunnel(
        settings: TunServiceSettings,
        systemProxyEndpoint: ProfileProxyEndpoint?,
    ): ParcelFileDescriptor? {
        val builder = Builder()
        builder.setSession("ClashRS VPNService")
        builder.addAddress(TUN_GATEWAY_V4, TUN_PREFIX_V4)
        TunRoutePolicy.ipv4Routes(
            bypassPrivateNetwork = settings.bypassPrivateNetwork,
            publicRoutes = resources.getStringArray(R.array.bypass_private_route).toList(),
            dnsServer = TUN_DNS_V4,
        ).forEach { route -> builder.addRoute(route.addressPart(), route.prefixPart()) }
        builder.addDnsServer(TUN_DNS_V4)
        if (settings.ipv6) {
            builder.addAddress(TUN_GATEWAY_V6, TUN_PREFIX_V6)
            TunRoutePolicy.ipv6Routes(
                ipv6Enabled = true,
                bypassPrivateNetwork = settings.bypassPrivateNetwork,
                publicRoutes = resources.getStringArray(R.array.bypass_private_route6).toList(),
                dnsServer = TUN_DNS_V6,
            ).forEach { route -> builder.addRoute(route.addressPart(), route.prefixPart()) }
            builder.addDnsServer(TUN_DNS_V6)
        }
        TunAppFilter.apply(builder, settings, packageName)
        if (settings.allowBypass) {
            builder.allowBypass()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && settings.systemProxy) {
            systemProxyEndpoint?.let { endpoint ->
                builder.setHttpProxy(
                    ProxyInfo.buildDirectProxy(
                        endpoint.host,
                        endpoint.port.toInt(),
                        systemProxyExclusions(settings.bypassPrivateNetwork),
                    ),
                )
            }
        }
        return builder.establish()
    }

    private fun systemProxyExclusions(bypassPrivateNetwork: Boolean): List<String> =
        SYSTEM_PROXY_DOMAIN_EXCLUSIONS +
            if (bypassPrivateNetwork) SYSTEM_PROXY_LOCAL_EXCLUSIONS else emptyList()

    private fun startUnderlyingNetworkTracking(applyNetworksToVpn: Boolean) {
        networkResetGeneration.invalidate()
        networkCoordinator?.stop()
        networkCoordinator =
            UnderlyingNetworkCoordinator(
                connectivityManager = getSystemService(ConnectivityManager::class.java),
                applyNetworks = if (applyNetworksToVpn) ::setUnderlyingNetworks else null,
                onPrimaryNetworkChanged = ::handleUnderlyingNetworkChange,
            ).also(UnderlyingNetworkCoordinator::start)
    }

    private fun handleUnderlyingNetworkChange() {
        val generation = networkResetGeneration.next()
        appendRuntimeLog("underlying network changed")
        serviceScope.launch {
            val serviceState = BackendRuntimeState.serviceState.first {
                !NetworkResetServiceStatePolicy.shouldWaitForStartup(it)
            }
            if (!NetworkResetServiceStatePolicy.shouldReset(serviceState)) return@launch
            networkResetMutex.withLock {
                if (
                    !NetworkResetServiceStatePolicy.shouldReset(
                        BackendRuntimeState.serviceState.value,
                    )
                ) return@withLock
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

    private fun String.addressPart(): String = substringBefore('/')

    private fun String.prefixPart(): Int = substringAfter('/').toInt()

    private fun closeDetachedTunFd(fd: Int) {
        if (fd < 0) return
        runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
            .onFailure { error ->
                PrivacySafeLog.warning(TAG, "Failed to close detached TUN fd", error, debugDetail = "fd=$fd")
                appendRuntimeLog("failed to close detached tun fd: $fd", error)
            }
    }

    private fun publishVpnSystemStatus(
        serviceActive: Boolean,
        routesSystemTraffic: Boolean,
    ) {
        val systemModes = readVpnSystemModes()
        val status = VpnSystemStatusPolicy.observed(
            apiLevel = Build.VERSION.SDK_INT,
            serviceActive = serviceActive,
            routesSystemTraffic = routesSystemTraffic,
            alwaysOn = systemModes.alwaysOn,
            lockdown = systemModes.lockdown,
            observedAt = System.currentTimeMillis(),
        )
        BackendRuntimeState.updateVpnSystemStatus(status)
        appendRuntimeLog(
            "vpn system status observed: active=${status.serviceActive} " +
                "routes_system_traffic=${status.routesSystemTraffic} " +
                "always_on=${status.alwaysOn} lockdown=${status.lockdown}",
        )
    }

    private fun effectiveTunSettings(settings: TunServiceSettings): TunServiceSettings {
        return effectiveTunSettings(
            settings = settings,
            systemLockdownEnabled = readVpnSystemModes(
                systemAlwaysOnStart = systemAlwaysOnStartObserved,
            ).lockdown,
        )
    }

    private fun effectiveTunSettings(
        settings: TunServiceSettings,
        systemLockdownEnabled: Boolean,
    ): TunServiceSettings {
        val routeSystemTraffic = VpnSystemStatusPolicy.shouldRouteSystemTraffic(
            apiLevel = Build.VERSION.SDK_INT,
            configured = settings.routeSystemTraffic,
            lockdown = systemLockdownEnabled,
        )
        return if (routeSystemTraffic == settings.routeSystemTraffic) {
            settings
        } else {
            settings.copy(routeSystemTraffic = routeSystemTraffic)
        }
    }

    private fun readVpnSystemModes(
        systemAlwaysOnStart: Boolean = systemAlwaysOnStartObserved,
    ): VpnSystemModes = VpnSystemModeReader.read(
        context = this,
        frameworkAlwaysOn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isAlwaysOn,
        frameworkLockdown = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isLockdownEnabled,
        systemAlwaysOnStart = systemAlwaysOnStart,
    )

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

    private fun requestLifecycleCleanup(stopService: Boolean = false) {
        val startupJob = cancelStartup()
        serviceScope.launch {
            startupJob?.join()
            runtimeMutex.withLock {
                if (cleanup().performed && stopService) stopSelf()
            }
        }
    }

    private fun cleanup(
        finalState: ServiceState = ServiceState.STOPPED,
        stopCore: Boolean = true,
        errorMessage: String? = null,
    ): VpnRuntimeCleanupOutcome = cleanupCoordinator.cleanup(
        finalState = finalState,
        stopCore = stopCore,
        errorMessage = errorMessage,
    )

    private fun releaseRuntime(
        stopCore: Boolean,
        removeForeground: Boolean,
    ): Throwable? {
        trafficNotificationJob?.cancel()
        trafficNotificationJob = null
        networkResetGeneration.invalidate()
        networkCoordinator?.stop()
        networkCoordinator = null
        var coreStopFailure: Throwable? = null
        if (stopCore) {
            shutdownClash().onSuccess {
                Global.updateActiveCoreWorkDirectory(null)
            }.exceptionOrNull()?.let { error ->
                coreStopFailure = error
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
        return coreStopFailure
    }

    override suspend fun restartVpn() {
        restartVpn(restartOnlyIfSystemRoutingIsMissing = false)
    }

    private suspend fun restartVpnForSystemRouting() {
        restartVpn(restartOnlyIfSystemRoutingIsMissing = true)
    }

    private suspend fun restartVpn(restartOnlyIfSystemRoutingIsMissing: Boolean) {
        check(lifecycleGate.canRunRuntime()) { "VPN service is stopping" }

        var coreShutdownFailed = false
        try {
            runtimeMutex.withLock {
                check(lifecycleGate.canRunRuntime()) { "VPN service is stopping" }
                if (restartOnlyIfSystemRoutingIsMissing) {
                    val routeSystemTraffic = effectiveTunSettings(
                        TunServiceSettingsLoader.load(this),
                    ).routeSystemTraffic
                    val shouldRestoreRoute =
                        VpnSystemStatusPolicy.shouldRestartForSystemTrafficRoutingAfterStartup(
                            serviceRunning =
                                BackendRuntimeState.serviceState.value == ServiceState.RUNNING,
                            tunnelActive = vpnInterface != null,
                            shouldRouteSystemTraffic = routeSystemTraffic,
                        )
                    if (!shouldRestoreRoute) {
                        appendRuntimeLog("skipped Lockdown route restart; current tunnel is sufficient")
                        return@withLock
                    }
                }
                BackendRuntimeState.updateServiceState(ServiceState.STOPPING)
                appendRuntimeLog("service runtime reload requested")
                ensureForegroundService()
                releaseRuntime(stopCore = true, removeForeground = false)?.let { error ->
                    coreShutdownFailed = true
                    throw error
                }

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
            val detail = error.toUserVisibleMessage(
                this@TunService,
                rs.chimera.android.R.string.profile_unknown_error,
            )
            withVpnCleanupContext {
                runtimeMutex.withLock {
                    val cleanupOutcome = cleanup(
                        finalState = ServiceState.ERROR,
                        stopCore = !coreShutdownFailed,
                        errorMessage = detail,
                    )
                    cleanupOutcome.releaseFailure?.let(error::addSuppressed)
                    if (cleanupOutcome.performed) {
                        NotificationHelper.notifyFailed(this@TunService, detail)
                        stopSelf()
                    }
                }
            }
            throw error
        }
    }

    override fun onCoreStopped(message: String) {
        Global.updateActiveCoreWorkDirectory(null)
        val detail = sanitizeUserVisibleErrorText(
            value = message,
            fallback = getString(rs.chimera.android.R.string.profile_unknown_error),
            privatePathPrefixes = listOf(applicationInfo.dataDir),
        )
        serviceScope.launch {
            runtimeMutex.withLock {
                if (!lifecycleGate.canHandleUnexpectedCoreStop()) return@withLock
                recordDesiredStop(VpnDesiredStateReason.CORE_EXITED)

                val cleanupOutcome = cleanup(
                    finalState = ServiceState.ERROR,
                    stopCore = false,
                    errorMessage = detail,
                )
                if (!cleanupOutcome.performed) return@withLock

                PrivacySafeLog.errorDetail(TAG, "Rust core stopped unexpectedly", detail)
                appendRuntimeLog("rust core stopped unexpectedly: $detail")
                NotificationHelper.notifyFailed(this@TunService, detail)
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
            val cleanupOutcome = cleanup()
            if (cleanupOutcome.performed) {
                stopSelf()
            }
            cleanupOutcome.releaseFailure?.let { throw it }
        }
    }

    override fun protectSocket(fd: Int): Boolean = vpnInterface == null || protect(fd)

    private companion object {
        const val TAG = "ChimeraTunService"
        val SYSTEM_PROXY_DOMAIN_EXCLUSIONS = listOf(
            "*zhihu.com",
            "*zhimg.com",
            "*jd.com",
            "100ime-iat-api.xfyun.cn",
            "*360buyimg.com",
        )
        val SYSTEM_PROXY_LOCAL_EXCLUSIONS = listOf(
            "localhost",
            "*.local",
            "127.*",
            "10.*",
            "172.16.*",
            "172.17.*",
            "172.18.*",
            "172.19.*",
            "172.2*",
            "172.30.*",
            "172.31.*",
            "192.168.*",
        )
    }
}
