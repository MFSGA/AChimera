package rs.chimera.android.service

import android.os.Build
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VpnSystemModeReaderInstrumentedTest {
    @Test
    fun readerReflectsAndroidSettingsForTheCurrentApp() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lockdownSetting = Settings.Secure.getInt(
            context.contentResolver,
            ALWAYS_ON_VPN_LOCKDOWN,
            0,
        ) == 1
        val actual = VpnSystemModeReader.read(
            context = context,
            frameworkAlwaysOn = false,
            frameworkLockdown = false,
            systemAlwaysOnStart = true,
        )

        assertEquals(true, actual.alwaysOn)
        assertEquals(lockdownSetting, actual.lockdown)
    }

    private companion object {
        const val ALWAYS_ON_VPN_LOCKDOWN = "always_on_vpn_lockdown"
    }
}
