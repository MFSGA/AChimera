package rs.chimera.android.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.SettingsDefaults

class RuntimeSettingsReaderTest {
    @Test
    fun emptyPreferencesUseSharedDefaults() {
        assertEquals(RuntimeSettings(), RuntimeSettingsReader.read(emptyMap<String, Any>()))
    }

    @Test
    fun legacyPortRepresentationsRemainSupported() {
        val settings = RuntimeSettingsReader.read(
            mapOf("mixed_port" to " 7891 ", "http_port" to 8080L, "socks_port" to 1080),
        )
        assertEquals(7891.toUShort(), settings.mixedPort)
        assertEquals(8080.toUShort(), settings.httpPort)
        assertEquals(1080.toUShort(), settings.socksPort)
    }

    @Test
    fun invalidPortsAndUnknownFilterUseSafeDefaults() {
        val settings = RuntimeSettingsReader.read(
            mapOf("mixed_port" to 0, "http_port" to 65536, "app_filter_mode" to "unknown"),
        )
        assertEquals(SettingsDefaults.MIXED_PORT, settings.mixedPort)
        assertNull(settings.httpPort)
        assertEquals("ALL", settings.appFilterMode)
    }

    @Test
    fun preferenceSetsCannotMutatePublishedSnapshot() {
        val packages = mutableSetOf("app.one")
        val settings = RuntimeSettingsReader.read(mapOf("allowed_apps" to packages))
        packages.add("app.two")
        assertEquals(setOf("app.one"), settings.allowedApps)
    }
}
