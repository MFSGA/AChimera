package rs.chimera.android

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.settings.PreferenceSettingsRepository
import rs.chimera.android.settings.RuntimeSettings
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsRepositoryInstrumentedTest {
    @Test
    fun twoReadersObserveTheSameSavedSettingsAndLegacyWrites() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences(
            "settings-repository-test-${UUID.randomUUID()}",
            Context.MODE_PRIVATE,
        )
        instrumentation.runOnMainSync {
            val first = PreferenceSettingsRepository(prefs)
            val second = PreferenceSettingsRepository(prefs)
            try {
                first.update(SettingsPatch(allowLan = true, httpPort = 8080.toUShort()))
                assertTrue(second.settings.value.allowLan)
                assertEquals(8080.toUShort(), second.settings.value.httpPort)
                assertEquals(first.settings.value, second.settings.value)
                first.update(SettingsPatch(clearHttpPort = true))
                assertNull(second.settings.value.httpPort)
                prefs.edit().putString("mixed_port", "7892").apply()
                assertEquals(7892.toUShort(), first.settings.value.mixedPort)
                assertEquals(first.settings.value, second.settings.value)
                prefs.edit().clear().apply()
                assertEquals(RuntimeSettings(), first.snapshot())
                assertEquals(first.snapshot(), second.snapshot())
            } finally {
                prefs.edit().clear().commit()
            }
        }
    }
}
