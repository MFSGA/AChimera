package rs.chimera.android.service

import rs.chimera.android.settings.RuntimeSettings

import rs.chimera.android.settings.SettingsProvider

import android.content.Context
import rs.chimera.android.ffi.ProfileOverride

internal typealias TunServiceSettings = RuntimeSettings

internal object TunServiceSettingsLoader {
    fun load(context: Context): TunServiceSettings =
        SettingsProvider.provide(context).snapshot()

    fun createProfileOverride(
        currentTunFd: Int?,
        settings: TunServiceSettings,
        logFilePath: String,
    ): ProfileOverride =
        ProfileOverride(
            tunFd = currentTunFd ?: 0,
            logFilePath = logFilePath,
            tunEnabled = settings.routeSystemTraffic,
            dnsHijacking = settings.dnsHijacking,
            allowLan = settings.allowLan,
            mixedPort = settings.mixedPort,
            httpPort = settings.httpPort,
            socksPort = settings.socksPort,
            fakeIp = settings.fakeIp,
            ipv6 = settings.ipv6,
        )
}

internal object TunRoutePolicy {
    fun ipv4Routes(
        bypassPrivateNetwork: Boolean,
        publicRoutes: List<String>,
        dnsServer: String,
    ): List<String> = if (bypassPrivateNetwork) {
        publicRoutes + "$dnsServer/32"
    } else {
        listOf("0.0.0.0/0")
    }

    fun ipv6Routes(
        ipv6Enabled: Boolean,
        bypassPrivateNetwork: Boolean,
        publicRoutes: List<String>,
        dnsServer: String,
    ): List<String> = when {
        !ipv6Enabled -> emptyList()
        bypassPrivateNetwork -> publicRoutes + "$dnsServer/128"
        else -> listOf("::/0")
    }
}
