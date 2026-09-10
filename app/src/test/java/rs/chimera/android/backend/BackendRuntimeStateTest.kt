package rs.chimera.android.backend

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState

class BackendRuntimeStateTest {
    @After
    fun resetState() {
        BackendRuntimeState.updateServiceState(ServiceState.STOPPED)
    }

    @Test
    fun serviceErrorMovesStateToError() {
        BackendRuntimeState.updateServiceError("core failed")

        assertEquals(ServiceState.ERROR, BackendRuntimeState.serviceState.value)
        assertEquals("core failed", BackendRuntimeState.serviceError.value)
    }

    @Test
    fun errorStatePreservesCurrentErrorMessage() {
        BackendRuntimeState.updateServiceError("core failed")
        BackendRuntimeState.updateServiceState(ServiceState.ERROR)

        assertEquals("core failed", BackendRuntimeState.serviceError.value)
    }

    @Test
    fun nonErrorStateClearsPreviousError() {
        BackendRuntimeState.updateServiceError("core failed")
        BackendRuntimeState.updateServiceState(ServiceState.STARTING)

        assertEquals(ServiceState.STARTING, BackendRuntimeState.serviceState.value)
        assertNull(BackendRuntimeState.serviceError.value)
    }

    @Test
    fun newerServiceErrorReplacesPreviousMessage() {
        BackendRuntimeState.updateServiceError("first failure")
        BackendRuntimeState.updateServiceError("second failure")

        assertEquals(ServiceState.ERROR, BackendRuntimeState.serviceState.value)
        assertEquals("second failure", BackendRuntimeState.serviceError.value)
    }

    @Test
    fun statusPublishesStateAndErrorTogether() {
        BackendRuntimeState.updateServiceError("core failed")
        assertEquals(ServiceState.ERROR, BackendRuntimeState.status.value.state)
        assertEquals("core failed", BackendRuntimeState.status.value.error)
        BackendRuntimeState.updateServiceState(ServiceState.STARTING)
        assertEquals(ServiceState.STARTING, BackendRuntimeState.status.value.state)
        assertNull(BackendRuntimeState.status.value.error)
        assertEquals(BackendRuntimeState.status.value.state, BackendRuntimeState.serviceState.value)
    }
}
