package rs.chimera.android.ui.onboarding

import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.ui.preferences.UiVariant

internal enum class OnboardingStep {
    WELCOME,
    UI_SELECTION,
    PROFILE,
    CONNECT,
    COMPLETE,
}

internal object OnboardingPolicy {
    fun shouldStartFirstRun(
        completed: Boolean,
        firstInstallTime: Long,
        lastUpdateTime: Long,
    ): Boolean = !completed && firstInstallTime == lastUpdateTime

    fun stepAfterUiSelection(hasProfiles: Boolean): OnboardingStep =
        if (hasProfiles) OnboardingStep.CONNECT else OnboardingStep.PROFILE

    fun shouldReturnToConnect(step: OnboardingStep, serviceState: ServiceState): Boolean =
        step == OnboardingStep.COMPLETE && serviceState != ServiceState.RUNNING

    fun defaultRecommendedUi(): UiVariant = UiVariant.WATFAQ
}

internal enum class ProfileImportFailureType {
    INVALID_URL,
    HTTP_AUTH,
    HTTP_NOT_FOUND,
    HTTP_SERVER,
    HTTP_OTHER,
    TIMEOUT,
    NETWORK,
    TLS,
    REDIRECT,
    TOO_LARGE,
    EMPTY_RESPONSE,
    HTML_RESPONSE,
    INVALID_CONFIGURATION,
    LOCAL_FILE,
    UNKNOWN,
}

internal data class ProfileImportFailure(
    val type: ProfileImportFailureType,
    val httpStatus: Int? = null,
)

internal object ProfileImportFailurePolicy {
    private val httpStatusPattern = Regex("\\bHTTP\\s+(\\d{3})\\b", RegexOption.IGNORE_CASE)

    fun classify(message: String?, isRemoteImport: Boolean): ProfileImportFailure {
        val normalized = message.orEmpty().lowercase()
        if (normalized.contains("remote profile url must use http") ||
            normalized.contains("invalid url") ||
            normalized.contains("url is invalid")
        ) {
            return ProfileImportFailure(ProfileImportFailureType.INVALID_URL)
        }

        val status = httpStatusPattern.find(message.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (status != null) {
            val type = when {
                status == 401 || status == 403 -> ProfileImportFailureType.HTTP_AUTH
                status == 404 || status == 410 -> ProfileImportFailureType.HTTP_NOT_FOUND
                status >= 500 -> ProfileImportFailureType.HTTP_SERVER
                else -> ProfileImportFailureType.HTTP_OTHER
            }
            return ProfileImportFailure(type, status)
        }

        val localFileFailure = listOf(
            "unable to open selected profile",
            "contentresolver",
            "failed to read selected file",
            "no such file or directory",
            "open failed",
        ).any(normalized::contains)
        val tooLarge = listOf("maximum size", "exceeds maximum", "download exceeds")
            .any(normalized::contains)
        val type = when {
            "timed out" in normalized || "timeout" in normalized -> ProfileImportFailureType.TIMEOUT
            listOf("certificate", "tls", "ssl", "peer cert").any(normalized::contains) ->
                ProfileImportFailureType.TLS
            "redirect limit" in normalized || "too many redirects" in normalized ->
                ProfileImportFailureType.REDIRECT
            tooLarge -> ProfileImportFailureType.TOO_LARGE
            "downloaded profile is empty" in normalized || "empty profile" in normalized ->
                ProfileImportFailureType.EMPTY_RESPONSE
            "downloaded content is html" in normalized || "html, not a profile" in normalized ->
                ProfileImportFailureType.HTML_RESPONSE
            listOf(
                "invalid config",
                "invalid runtime config",
                "config file is empty",
                "failed to parse profile",
                "failed to validate runtime proxies",
                "failed to build runtime config",
            ).any(normalized::contains) ->
                ProfileImportFailureType.INVALID_CONFIGURATION
            localFileFailure -> ProfileImportFailureType.LOCAL_FILE
            listOf(
                "failed to send request",
                "dns",
                "resolve",
                "connection refused",
                "connection reset",
                "network is unreachable",
                "no address associated",
            ).any(normalized::contains) -> ProfileImportFailureType.NETWORK
            !isRemoteImport -> ProfileImportFailureType.LOCAL_FILE
            else -> ProfileImportFailureType.UNKNOWN
        }

        return ProfileImportFailure(type)
    }
}
