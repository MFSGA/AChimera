package rs.chimera.android.service

import android.net.VpnService
import rs.chimera.android.util.PrivacySafeLog
import rs.chimera.android.util.runCatchingRecoverable

internal object TunAppFilter {
    fun apply(
        builder: VpnService.Builder,
        settings: TunServiceSettings,
        servicePackageName: String,
    ) {
        when (settings.appFilterMode) {
            "ALLOWED" -> {
                settings.allowedApps.forEach { appPackageName ->
                    runCatchingRecoverable { builder.addAllowedApplication(appPackageName) }
                        .onFailure { error ->
                            PrivacySafeLog.warning(
                                TAG,
                                "Failed to add allowed app",
                                error,
                                debugDetail = appPackageName,
                            )
                        }
                }
            }

            "DISALLOWED" -> {
                builder.addDisallowedApplication(servicePackageName)
                settings.disallowedApps.forEach { appPackageName ->
                    addDisallowedApplicationSafely(builder, appPackageName)
                }
            }

            else -> builder.addDisallowedApplication(servicePackageName)
        }
    }

    private fun addDisallowedApplicationSafely(
        builder: VpnService.Builder,
        appPackageName: String,
    ) {
        runCatchingRecoverable { builder.addDisallowedApplication(appPackageName) }
            .onFailure { error ->
                PrivacySafeLog.warning(
                    TAG,
                    "Failed to add disallowed app",
                    error,
                    debugDetail = appPackageName,
                )
                TunRuntimeFiles.appendRuntimeLog(
                    "failed to add disallowed app: $appPackageName",
                    error,
                )
            }
    }

    private const val TAG = "ChimeraTunService"
}
