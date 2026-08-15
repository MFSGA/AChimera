package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.SettingsApplyEffect
import rs.chimera.android.backend.model.SettingsPatch

class SettingsPatchTest {
    @Test
    fun emptyPatchIsImmediate() {
        assertEquals(
            SettingsApplyEffect.IMMEDIATE,
            SettingsPatch().requiredApplyEffect(),
        )
    }

    @Test
    fun listenerAndDnsSettingsRestartCore() {
        listOf(
            SettingsPatch(allowLan = true),
            SettingsPatch(mixedPort = 7890u),
            SettingsPatch(httpPort = 8080u),
            SettingsPatch(socksPort = 1080u),
            SettingsPatch(clearHttpPort = true),
            SettingsPatch(clearSocksPort = true),
            SettingsPatch(fakeIp = true),
        ).forEach { patch ->
            assertEquals(
                SettingsApplyEffect.RESTART_CORE,
                patch.requiredApplyEffect(),
            )
        }
    }

    @Test
    fun tunnelSettingsRequireTunRebuild() {
        listOf(
            SettingsPatch(ipv6 = true),
            SettingsPatch(appFilterMode = "ALLOWED"),
            SettingsPatch(allowedApps = setOf("example.allowed")),
            SettingsPatch(disallowedApps = setOf("example.disallowed")),
        ).forEach { patch ->
            assertEquals(
                SettingsApplyEffect.REBUILD_TUN,
                patch.requiredApplyEffect(),
            )
        }
    }

    @Test
    fun tunRebuildTakesPriorityOverCoreRestart() {
        val patch = SettingsPatch(
            mixedPort = 7890u,
            ipv6 = true,
        )

        assertEquals(
            SettingsApplyEffect.REBUILD_TUN,
            patch.requiredApplyEffect(),
        )
    }
}
