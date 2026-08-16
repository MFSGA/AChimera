package rs.chimera.android.ui.metacubex.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rs.chimera.android.backend.model.ServiceState
import uniffi.chimera_ffi.Mode

class MetaMainModePolicyTest {
    @Test
    fun runningServiceKeepsReportedMode() {
        assertEquals(
            Mode.GLOBAL,
            MetaMainModePolicy.visibleMode(ServiceState.RUNNING, Mode.GLOBAL),
        )
    }

    @Test
    fun nonRunningServiceNeverShowsStaleMode() {
        assertNull(MetaMainModePolicy.visibleMode(ServiceState.STOPPED, Mode.RULE))
        assertNull(MetaMainModePolicy.visibleMode(ServiceState.ERROR, Mode.DIRECT))
        assertNull(MetaMainModePolicy.visibleMode(ServiceState.STARTING, Mode.GLOBAL))
        assertNull(MetaMainModePolicy.visibleMode(ServiceState.STOPPING, Mode.GLOBAL))
    }
}
