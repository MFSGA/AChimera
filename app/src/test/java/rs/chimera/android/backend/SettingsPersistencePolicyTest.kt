package rs.chimera.android.backend

import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsPersistencePolicyTest {
    @Test
    fun acceptsSuccessfulCommit() {
        SettingsPersistencePolicy.commit { true }
    }

    @Test
    fun rejectsFailedCommit() {
        assertThrows(IllegalStateException::class.java) {
            SettingsPersistencePolicy.commit { false }
        }
    }
}
