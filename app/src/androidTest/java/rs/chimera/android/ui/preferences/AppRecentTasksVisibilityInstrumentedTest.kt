package rs.chimera.android.ui.preferences

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRecentTasksVisibilityInstrumentedTest {
    @Test
    fun hideFromRecentsPreferencePersistsAndCanBeRestored() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = AppPreferences.hideFromRecents(context)

        try {
            AppPreferences.updateHideFromRecents(context, true)
            assertTrue(AppPreferences.hideFromRecents(context))

            AppPreferences.updateHideFromRecents(context, false)
            assertFalse(AppPreferences.hideFromRecents(context))
        } finally {
            AppPreferences.updateHideFromRecents(context, original)
        }
    }
}
