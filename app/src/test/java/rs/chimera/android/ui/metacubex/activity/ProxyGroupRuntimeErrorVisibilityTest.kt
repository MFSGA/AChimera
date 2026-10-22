package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rs.chimera.android.backend.model.BackendRuntimeErrorSource
import rs.chimera.android.backend.model.ServiceState

class ProxyGroupRuntimeErrorVisibilityTest {
    @Test
    fun proxyDelayFailureDoesNotReplacePageWhileDelayTestIsRunning() {
        assertFalse(
            shouldShowProxyGroupRuntimeError(
                errorSource = BackendRuntimeErrorSource.PROXY_GROUPS,
                serviceState = ServiceState.RUNNING,
                initialLoadComplete = true,
                selecting = false,
                refreshing = false,
                testing = true,
            ),
        )
    }

    @Test
    fun proxyGroupErrorIsVisibleWhenNoProxyOperationIsActive() {
        assertTrue(
            shouldShowProxyGroupRuntimeError(
                errorSource = BackendRuntimeErrorSource.PROXY_GROUPS,
                serviceState = ServiceState.RUNNING,
                initialLoadComplete = true,
                selecting = false,
                refreshing = false,
                testing = false,
            ),
        )
    }
}
