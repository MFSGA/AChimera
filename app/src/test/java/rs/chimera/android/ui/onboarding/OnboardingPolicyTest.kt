package rs.chimera.android.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState
import rs.chimera.android.ui.preferences.UiVariant

class OnboardingPolicyTest {
    @Test
    fun freshUncompletedInstallStartsGuide() {
        assertTrue(
            OnboardingPolicy.shouldStartFirstRun(
                completed = false,
                firstInstallTime = 100L,
                lastUpdateTime = 100L,
            ),
        )
    }

    @Test
    fun completedOrUpdatedInstallSkipsAutomaticGuide() {
        assertFalse(OnboardingPolicy.shouldStartFirstRun(true, 100L, 100L))
        assertFalse(OnboardingPolicy.shouldStartFirstRun(false, 100L, 200L))
    }

    @Test
    fun profileStepIsSkippedWhenAProfileAlreadyExists() {
        assertEquals(OnboardingStep.PROFILE, OnboardingPolicy.stepAfterUiSelection(false))
        assertEquals(OnboardingStep.CONNECT, OnboardingPolicy.stepAfterUiSelection(true))
    }

    @Test
    fun chimeraInterfaceIsTheRecommendedDefault() {
        assertEquals(UiVariant.WATFAQ, OnboardingPolicy.defaultRecommendedUi())
    }

    @Test
    fun completedStepReturnsToConnectWhenVpnIsNotRunning() {
        assertFalse(OnboardingPolicy.shouldReturnToConnect(OnboardingStep.COMPLETE, ServiceState.RUNNING))
        assertTrue(OnboardingPolicy.shouldReturnToConnect(OnboardingStep.COMPLETE, ServiceState.STOPPED))
        assertTrue(OnboardingPolicy.shouldReturnToConnect(OnboardingStep.COMPLETE, ServiceState.ERROR))
        assertFalse(OnboardingPolicy.shouldReturnToConnect(OnboardingStep.CONNECT, ServiceState.STOPPED))
    }
}

class ProfileImportFailurePolicyTest {
    @Test
    fun classifiesHttpFailuresWithoutLosingStatusCode() {
        assertEquals(
            ProfileImportFailure(ProfileImportFailureType.HTTP_AUTH, 403),
            ProfileImportFailurePolicy.classify("HTTP 403 - Forbidden", isRemoteImport = true),
        )
        assertEquals(
            ProfileImportFailure(ProfileImportFailureType.HTTP_NOT_FOUND, 404),
            ProfileImportFailurePolicy.classify("HTTP 404 - Not Found", isRemoteImport = true),
        )
        assertEquals(
            ProfileImportFailure(ProfileImportFailureType.HTTP_SERVER, 503),
            ProfileImportFailurePolicy.classify("HTTP 503 - Service Unavailable", isRemoteImport = true),
        )
    }

    @Test
    fun networkFailuresAreNotLabeledAsAnInvalidLink() {
        assertEquals(
            ProfileImportFailureType.TIMEOUT,
            ProfileImportFailurePolicy.classify("download request timed out", true).type,
        )
        assertEquals(
            ProfileImportFailureType.NETWORK,
            ProfileImportFailurePolicy.classify("failed to send request: DNS lookup failed", true).type,
        )
        assertEquals(
            ProfileImportFailureType.TLS,
            ProfileImportFailurePolicy.classify("invalid peer certificate", true).type,
        )
    }

    @Test
    fun classifiesDownloadedContentAndLocalFileErrors() {
        assertEquals(
            ProfileImportFailureType.HTML_RESPONSE,
            ProfileImportFailurePolicy.classify("Downloaded content is HTML, not a profile configuration", true).type,
        )
        assertEquals(
            ProfileImportFailureType.INVALID_CONFIGURATION,
            ProfileImportFailurePolicy.classify("invalid config: expected mapping", true).type,
        )
        assertEquals(
            ProfileImportFailureType.INVALID_CONFIGURATION,
            ProfileImportFailurePolicy.classify(
                "failed to parse profile /private/path/profile.yaml: invalid syntax",
                true,
            ).type,
        )
        assertEquals(
            ProfileImportFailureType.INVALID_CONFIGURATION,
            ProfileImportFailurePolicy.classify("config file is empty", true).type,
        )
        assertEquals(
            ProfileImportFailureType.LOCAL_FILE,
            ProfileImportFailurePolicy.classify("Unable to open selected profile", false).type,
        )
    }
}
