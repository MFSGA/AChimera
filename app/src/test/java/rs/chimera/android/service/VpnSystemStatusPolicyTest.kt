package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnSystemStatusPolicyTest {
    @Test
    fun systemAlwaysOnStartWithLockdownRequiresRouting() {
        val modes = VpnSystemModePolicy.resolve(
            lockdownSetting = true,
            frameworkAlwaysOn = false,
            frameworkLockdown = false,
            systemAlwaysOnStart = true,
        )

        assertTrue(modes.alwaysOn)
        assertTrue(modes.lockdown)
    }

    @Test
    fun systemAlwaysOnStartWithoutLockdownStillReportsAlwaysOn() {
        val modes = VpnSystemModePolicy.resolve(
            lockdownSetting = false,
            frameworkAlwaysOn = false,
            frameworkLockdown = false,
            systemAlwaysOnStart = true,
        )

        assertTrue(modes.alwaysOn)
        assertFalse(modes.lockdown)
    }

    @Test
    fun lockdownSettingAloneDoesNotIdentifyThisAsTheAlwaysOnVpn() {
        val modes = VpnSystemModePolicy.resolve(
            lockdownSetting = true,
            frameworkAlwaysOn = false,
            frameworkLockdown = false,
            systemAlwaysOnStart = false,
        )

        assertFalse(modes.alwaysOn)
        assertFalse(modes.lockdown)
    }

    @Test
    fun frameworkVpnModesRemainAvailableWithoutSystemStartIntent() {
        val modes = VpnSystemModePolicy.resolve(
            lockdownSetting = false,
            frameworkAlwaysOn = true,
            frameworkLockdown = true,
            systemAlwaysOnStart = false,
        )

        assertTrue(modes.alwaysOn)
        assertTrue(modes.lockdown)
    }

    @Test
    fun activeAlwaysOnVpnUsesReadableLockdownSettingWhenFrameworkFlagLags() {
        val modes = VpnSystemModePolicy.resolve(
            lockdownSetting = true,
            frameworkAlwaysOn = true,
            frameworkLockdown = false,
            systemAlwaysOnStart = false,
        )

        assertTrue(modes.alwaysOn)
        assertTrue(modes.lockdown)
    }

    @Test
    fun lockdownRequiresSystemTrafficRoutingOnSupportedAndroidVersions() {
        assertTrue(
            VpnSystemStatusPolicy.shouldRouteSystemTraffic(
                apiLevel = 29,
                configured = false,
                lockdown = true,
            ),
        )
    }

    @Test
    fun lockdownDoesNotRequireSystemTrafficRoutingOnUnsupportedAndroidVersions() {
        assertFalse(
            VpnSystemStatusPolicy.shouldRouteSystemTraffic(
                apiLevel = 28,
                configured = false,
                lockdown = true,
            ),
        )
    }

    @Test
    fun configuredSystemTrafficRoutingRemainsEnabledWithoutLockdown() {
        assertTrue(
            VpnSystemStatusPolicy.shouldRouteSystemTraffic(
                apiLevel = 35,
                configured = true,
                lockdown = false,
            ),
        )
    }

    @Test
    fun disabledSystemTrafficRoutingRemainsDisabledWithoutLockdown() {
        assertFalse(
            VpnSystemStatusPolicy.shouldRouteSystemTraffic(
                apiLevel = 35,
                configured = false,
                lockdown = false,
            ),
        )
    }

    @Test
    fun runningCoreRestartsWhenItMustStartRoutingSystemTraffic() {
        assertTrue(
            VpnSystemStatusPolicy.shouldRestartForSystemTrafficRouting(
                serviceActive = true,
                tunnelActive = false,
                shouldRouteSystemTraffic = true,
            ),
        )
    }

    @Test
    fun existingTunnelDoesNotNeedLockdownRouteRestart() {
        assertFalse(
            VpnSystemStatusPolicy.shouldRestartForSystemTrafficRouting(
                serviceActive = true,
                tunnelActive = true,
                shouldRouteSystemTraffic = true,
            ),
        )
    }

    @Test
    fun stoppedServiceDoesNotScheduleLockdownRouteRestart() {
        assertFalse(
            VpnSystemStatusPolicy.shouldRestartForSystemTrafficRouting(
                serviceActive = false,
                tunnelActive = false,
                shouldRouteSystemTraffic = true,
            ),
        )
    }

    @Test
    fun startupDuplicateOnlyRestartsIfTheCompletedRuntimeStillLacksTheRequiredTunnel() {
        assertFalse(
            VpnSystemStatusPolicy.shouldRestartForSystemTrafficRoutingAfterStartup(
                serviceRunning = true,
                tunnelActive = true,
                shouldRouteSystemTraffic = true,
            ),
        )
        assertTrue(
            VpnSystemStatusPolicy.shouldRestartForSystemTrafficRoutingAfterStartup(
                serviceRunning = true,
                tunnelActive = false,
                shouldRouteSystemTraffic = true,
            ),
        )
        assertFalse(
            VpnSystemStatusPolicy.shouldRestartForSystemTrafficRoutingAfterStartup(
                serviceRunning = false,
                tunnelActive = false,
                shouldRouteSystemTraffic = true,
            ),
        )
    }

    @Test
    fun unsupportedApiLevelsDoNotReportUnavailableSystemModes() {
        val status = VpnSystemStatusPolicy.observed(
            apiLevel = 28,
            serviceActive = true,
            alwaysOn = true,
            lockdown = true,
            observedAt = 1_000,
        )

        assertTrue(status.observed)
        assertTrue(status.serviceActive)
        assertTrue(status.routesSystemTraffic)
        assertFalse(status.alwaysOn)
        assertFalse(status.lockdown)
    }

    @Test
    fun supportedApiLevelsPreserveObservedModes() {
        val status = VpnSystemStatusPolicy.observed(
            apiLevel = 29,
            serviceActive = true,
            alwaysOn = true,
            lockdown = true,
            observedAt = 2_000,
        )

        assertTrue(status.alwaysOn)
        assertTrue(status.lockdown)
        assertEquals(2_000, status.observedAt)
    }

    @Test
    fun inactiveCoreCanStillReportAlwaysOnAndLockdownSettings() {
        val status = VpnSystemStatusPolicy.observed(
            apiLevel = 29,
            serviceActive = false,
            alwaysOn = true,
            lockdown = true,
            observedAt = 2_500,
        )

        assertTrue(status.observed)
        assertFalse(status.serviceActive)
        assertFalse(status.routesSystemTraffic)
        assertTrue(status.alwaysOn)
        assertTrue(status.lockdown)
    }

    @Test
    fun runningCoreWithoutTunKeepsFreshServiceStatusWithoutClaimingSystemRouting() {
        val status = VpnSystemStatusPolicy.observed(
            apiLevel = 35,
            serviceActive = true,
            routesSystemTraffic = false,
            alwaysOn = false,
            lockdown = false,
            observedAt = 3_000,
        )

        assertTrue(status.observed)
        assertTrue(status.serviceActive)
        assertFalse(status.routesSystemTraffic)
    }
}
