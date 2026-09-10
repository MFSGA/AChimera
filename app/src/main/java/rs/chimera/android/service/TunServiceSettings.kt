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
        currentTunFd: Int,
        settings: TunServiceSettings,
        logFilePath: String,
    ): ProfileOverride =
        ProfileOverride(
            tunFd = currentTunFd,
            logFilePath = logFilePath,
            allowLan = settings.allowLan,
            mixedPort = settings.mixedPort,
            httpPort = settings.httpPort,
            socksPort = settings.socksPort,
            fakeIp = settings.fakeIp,
            ipv6 = settings.ipv6,
        )
}
