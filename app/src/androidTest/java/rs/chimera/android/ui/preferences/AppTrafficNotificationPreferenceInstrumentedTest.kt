package rs.chimera.android.ui.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppTrafficNotificationPreferenceInstrumentedTest {
    @Test
    fun showTrafficDefaultsOnAndPersistsChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val key = "show_traffic_in_notification"
        val hadOriginalValue = preferences.contains(key)
        val originalValue = preferences.getBoolean(key, true)

        try {
            preferences.edit().remove(key).commit()
            assertTrue(AppPreferences.showTrafficInNotification(context))

            AppPreferences.updateShowTrafficInNotification(context, false)
            assertFalse(AppPreferences.showTrafficInNotification(context))

            AppPreferences.updateShowTrafficInNotification(context, true)
            assertTrue(AppPreferences.showTrafficInNotification(context))
        } finally {
            preferences.edit().apply {
                if (hadOriginalValue) putBoolean(key, originalValue) else remove(key)
            }.commit()
        }
    }
}
