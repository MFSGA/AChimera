package rs.chimera.android.service

import org.junit.Assert.assertEquals
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class VpnRuntimeCleanupPolicyTest {
    @Test
    fun coreShutdownFailureDoesNotReportSuccessfulStop() {
        val failure = IllegalStateException("core shutdown timed out")

        val decision = VpnRuntimeCleanupPolicy.resolve(
            requestedState = ServiceState.STOPPED,
            existingErrorMessage = null,
            releaseFailure = failure,
            formatReleaseFailure = { it.message.orEmpty() },
        )

        assertEquals(ServiceState.ERROR, decision.serviceState)
        assertEquals("core shutdown timed out", decision.errorMessage)
    }

    @Test
    fun cleanupFailureDoesNotReplaceOriginalRuntimeError() {
        val releaseFailure = IllegalStateException("core shutdown timed out")

        val decision = VpnRuntimeCleanupPolicy.resolve(
            requestedState = ServiceState.ERROR,
            existingErrorMessage = "profile initialization failed",
            releaseFailure = releaseFailure,
            formatReleaseFailure = { it.message.orEmpty() },
        )

        assertEquals(ServiceState.ERROR, decision.serviceState)
        assertEquals("profile initialization failed", decision.errorMessage)
    }

    @Test
    fun successfulCleanupPreservesRequestedState() {
        val decision = VpnRuntimeCleanupPolicy.resolve(
            requestedState = ServiceState.STOPPED,
            existingErrorMessage = null,
            releaseFailure = null,
            formatReleaseFailure = { it.message.orEmpty() },
        )

        assertEquals(ServiceState.STOPPED, decision.serviceState)
        assertEquals(null, decision.errorMessage)
    }
}
