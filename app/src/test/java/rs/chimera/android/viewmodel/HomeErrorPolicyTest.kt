package rs.chimera.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.BackendRuntimeError
import rs.chimera.android.backend.model.BackendRuntimeErrorSource

class HomeErrorPolicyTest {
    @Test
    fun serviceErrorTakesPriority() {
        val runtimeError = BackendRuntimeError(
            source = BackendRuntimeErrorSource.PROXY_GROUPS,
            message = "runtime error",
        )

        assertEquals("service error", HomeErrorPolicy.resolve("service error", runtimeError))
    }

    @Test
    fun runtimeErrorIsUsedWhenServiceIsHealthy() {
        val runtimeError = BackendRuntimeError(
            source = BackendRuntimeErrorSource.TRAFFIC,
            message = "runtime error",
        )

        assertEquals("runtime error", HomeErrorPolicy.resolve(null, runtimeError))
    }

    @Test
    fun blankServiceErrorDoesNotHideRuntimeError() {
        val runtimeError = BackendRuntimeError(
            source = BackendRuntimeErrorSource.MEMORY,
            message = "runtime error",
        )

        assertEquals("runtime error", HomeErrorPolicy.resolve("   ", runtimeError))
    }

    @Test
    fun noErrorsProducesHealthyState() {
        assertNull(HomeErrorPolicy.resolve(null, null))
    }
}
