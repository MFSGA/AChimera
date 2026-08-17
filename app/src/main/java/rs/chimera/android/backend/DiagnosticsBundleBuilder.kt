package rs.chimera.android.backend

import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.ProfileSummary
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.backend.model.VpnSystemStatus
import rs.chimera.android.service.RuntimeLogSanitizer

internal data class DiagnosticsBundleInput(
    val generatedAtEpochMillis: Long,
    val appVersion: String,
    val appVersionCode: Long,
    val androidSdk: Int,
    val serviceState: ServiceState,
    val serviceError: String?,
    val vpnSystemStatus: VpnSystemStatus,
    val activeProfile: ProfileSummary?,
    val runtimeError: BackendRuntimeError?,
    val runtimeLogs: String,
    val privatePathPrefixes: List<String> = emptyList(),
)

internal object DiagnosticsBundleBuilder {
    private const val FORMAT_VERSION = 1
    private const val NONE = "none"
    private const val APP_PRIVATE = "<app-private>"

    fun build(input: DiagnosticsBundleInput): String = buildString {
        appendLine("chimera_diagnostics_version=$FORMAT_VERSION")
        appendLine("generated_at_epoch_ms=${input.generatedAtEpochMillis}")
        appendLine("app_version=${input.safeSingleLine(input.appVersion)}")
        appendLine("app_version_code=${input.appVersionCode}")
        appendLine("android_sdk=${input.androidSdk}")

        appendLine()
        appendLine("[service]")
        appendLine("state=${input.serviceState.name}")
        appendLine("error=${input.safeNullable(input.serviceError)}")

        appendLine()
        appendLine("[vpn]")
        appendLine("observed=${input.vpnSystemStatus.observed}")
        appendLine("service_active=${input.vpnSystemStatus.serviceActive}")
        appendLine("always_on=${input.vpnSystemStatus.alwaysOn}")
        appendLine("lockdown=${input.vpnSystemStatus.lockdown}")
        appendLine("observed_at_epoch_ms=${input.vpnSystemStatus.observedAt}")

        appendLine()
        appendLine("[active_profile]")
        appendProfile(input)

        appendLine()
        appendLine("[backend_runtime_error]")
        appendRuntimeError(input)

        appendLine()
        appendLine("[runtime_logs]")
        append(input.safeMultiline(input.runtimeLogs).ifBlank { NONE })
        appendLine()
    }

    private fun StringBuilder.appendProfile(input: DiagnosticsBundleInput) {
        val profile = input.activeProfile
        if (profile == null) {
            appendLine("present=false")
            return
        }

        appendLine("present=true")
        appendLine("type=${profile.type.name}")
        appendLine("remote=${profile.isRemote}")
        appendLine("file_size_bytes=${profile.fileSize}")
        appendLine("created_at_epoch_ms=${profile.createdAt}")
        appendLine("last_updated_epoch_ms=${profile.lastUpdated ?: NONE}")
        appendLine("auto_update=${profile.autoUpdate}")
        appendLine("last_auto_update_attempt_epoch_ms=${profile.lastAutoUpdateAttempt ?: NONE}")
        appendLine("auto_update_failures=${profile.autoUpdateFailures}")
        appendLine("next_auto_update_epoch_ms=${profile.nextAutoUpdateAt ?: NONE}")
        appendLine("last_auto_update_error=${input.safeNullable(profile.lastAutoUpdateError)}")
    }

    private fun StringBuilder.appendRuntimeError(input: DiagnosticsBundleInput) {
        val error = input.runtimeError
        if (error == null) {
            appendLine("present=false")
            return
        }

        appendLine("present=true")
        appendLine("source=${error.source.name}")
        appendLine("message=${input.safeSingleLine(error.message)}")
    }

    private fun DiagnosticsBundleInput.safeNullable(value: String?): String =
        value?.takeIf { it.isNotBlank() }?.let { safeSingleLine(it) } ?: NONE

    private fun DiagnosticsBundleInput.safeSingleLine(value: String): String =
        safeMultiline(value)
            .replace('\r', ' ')
            .replace('\n', ' ')
            .trim()
            .ifBlank { NONE }

    private fun DiagnosticsBundleInput.safeMultiline(value: String): String {
        val sanitized = RuntimeLogSanitizer.sanitizeText(value)
        return normalizedPrivatePathPrefixes().fold(sanitized) { text, prefix ->
            text.replace(prefix, APP_PRIVATE)
        }
    }

    private fun DiagnosticsBundleInput.normalizedPrivatePathPrefixes(): List<String> =
        privatePathPrefixes
            .asSequence()
            .map(String::trim)
            .map { it.trimEnd('/') }
            .filter(String::isNotBlank)
            .distinct()
            .sortedByDescending(String::length)
            .toList()
}
