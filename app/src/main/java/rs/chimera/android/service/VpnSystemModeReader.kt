package rs.chimera.android.service

import android.content.Context
import android.os.Build
import android.provider.Settings

internal data class VpnSystemModes(
    val alwaysOn: Boolean,
    val lockdown: Boolean,
)

internal object VpnSystemModeReader {
    fun read(
        context: Context,
        frameworkAlwaysOn: Boolean,
        frameworkLockdown: Boolean,
        systemAlwaysOnStart: Boolean,
    ): VpnSystemModes {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return VpnSystemModes(alwaysOn = false, lockdown = false)
        }

        val resolver = context.contentResolver
        val lockdownSetting = runCatching {
            Settings.Secure.getInt(resolver, ALWAYS_ON_VPN_LOCKDOWN, 0) == 1
        }.getOrDefault(false)

        return VpnSystemModePolicy.resolve(
            lockdownSetting = lockdownSetting,
            frameworkAlwaysOn = frameworkAlwaysOn,
            frameworkLockdown = frameworkLockdown,
            systemAlwaysOnStart = systemAlwaysOnStart,
        )
    }

    private const val ALWAYS_ON_VPN_LOCKDOWN = "always_on_vpn_lockdown"
}

internal object VpnSystemModePolicy {
    fun resolve(
        lockdownSetting: Boolean,
        frameworkAlwaysOn: Boolean,
        frameworkLockdown: Boolean,
        systemAlwaysOnStart: Boolean,
    ): VpnSystemModes {
        return VpnSystemModes(
            alwaysOn = frameworkAlwaysOn || systemAlwaysOnStart,
            lockdown = frameworkLockdown ||
                ((frameworkAlwaysOn || systemAlwaysOnStart) && lockdownSetting),
        )
    }
}
