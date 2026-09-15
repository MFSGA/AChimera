package rs.chimera.android.service

import android.net.VpnService
import rs.chimera.android.util.PrivacySafeLog

internal object TunAppFilter {
    fun apply(
        builder: VpnService.Builder,
        settings: TunServiceSettings,
        servicePackageName: String,
    ) {
        when (settings.appFilterMode) {
            "ALLOWED" -> {
                settings.allowedApps.forEach { appPackageName ->
                    runCatching { builder.addAllowedApplication(appPackageName) }
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
                addDisallowedApplicationSafely(builder, servicePackageName)
                settings.disallowedApps.forEach { appPackageName ->
                    addDisallowedApplicationSafely(builder, appPackageName)
                }
            }

            else -> addDisallowedApplicationSafely(builder, servicePackageName)
        }
    }

    private fun addDisallowedApplicationSafely(
        builder: VpnService.Builder,
        appPackageName: String,
    ) {
        runCatching { builder.addDisallowedApplication(appPackageName) }
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
