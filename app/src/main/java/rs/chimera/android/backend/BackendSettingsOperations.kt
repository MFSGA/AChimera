package rs.chimera.android.backend

import android.content.SharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.SettingsApplyEffect
import rs.chimera.android.backend.model.SettingsPatch

internal class BackendSettingsOperations(
    private val preferences: SharedPreferences,
    private val serviceState: StateFlow<ServiceState>,
    private val restartVpn: suspend () -> Unit,
) {
    private val updateMutex = Mutex()

    suspend fun update(patch: SettingsPatch): SettingsApplyEffect = updateMutex.withLock {
        withContext(Dispatchers.IO) {
            val applyEffect = patch.requiredApplyEffect()
            val editor = preferences.edit()
            patch.allowLan?.let { editor.putBoolean("allow_lan", it) }
            patch.mixedPort?.let { editor.putInt("mixed_port", it.toInt()) }
            if (patch.clearHttpPort) editor.remove("http_port")
            patch.httpPort?.let { editor.putInt("http_port", it.toInt()) }
            if (patch.clearSocksPort) editor.remove("socks_port")
            patch.socksPort?.let { editor.putInt("socks_port", it.toInt()) }
            patch.fakeIp?.let { editor.putBoolean("fake_ip", it) }
            patch.ipv6?.let { editor.putBoolean("ipv6", it) }
            patch.appFilterMode?.let { editor.putString("app_filter_mode", it) }
            patch.allowedApps?.let { editor.putStringSet("allowed_apps", it.toSet()) }
            patch.disallowedApps?.let { editor.putStringSet("disallowed_apps", it.toSet()) }
            SettingsPersistencePolicy.commit(editor::commit)

            if (applyEffect != SettingsApplyEffect.IMMEDIATE && serviceState.value == ServiceState.RUNNING) {
                try {
                    restartVpn()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw SettingsApplyException(error)
                }
            }
            applyEffect
        }
    }
}
