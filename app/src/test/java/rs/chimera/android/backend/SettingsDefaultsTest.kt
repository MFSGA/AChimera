package rs.chimera.android.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.SettingsApplyEffect
import rs.chimera.android.backend.model.SettingsDefaults

class SettingsDefaultsTest {
    @Test
    fun resetPatch_restoresRuntimeDefaultsAndRebuildsTun() {
        val patch = SettingsDefaults.resetPatch()

        assertEquals(false, patch.allowLan)
        assertEquals(SettingsDefaults.MIXED_PORT, patch.mixedPort)
        assertEquals(false, patch.fakeIp)
        assertEquals(false, patch.ipv6)
        assertTrue(patch.clearHttpPort)
        assertTrue(patch.clearSocksPort)
        assertEquals(SettingsDefaults.APP_FILTER_MODE, patch.appFilterMode)
        assertEquals(emptySet<String>(), patch.allowedApps)
        assertEquals(emptySet<String>(), patch.disallowedApps)
        assertEquals(SettingsApplyEffect.REBUILD_TUN, patch.requiredApplyEffect())
    }
}
