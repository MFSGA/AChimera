package rs.chimera.android.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class AppFilterModePreferenceTest {
    @Test
    fun parseRestoresPersistedMode() {
        assertEquals(AppFilterMode.ALLOWED, AppFilterModePreference.parse("ALLOWED"))
        assertEquals(AppFilterMode.DISALLOWED, AppFilterModePreference.parse("DISALLOWED"))
    }

    @Test
    fun parseFallsBackToAllForMissingOrUnknownMode() {
        assertEquals(AppFilterMode.ALL, AppFilterModePreference.parse(null))
        assertEquals(AppFilterMode.ALL, AppFilterModePreference.parse("UNKNOWN"))
    }
}
