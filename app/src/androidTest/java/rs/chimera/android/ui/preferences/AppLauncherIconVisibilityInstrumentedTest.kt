package rs.chimera.android.ui.preferences

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLauncherIconVisibilityInstrumentedTest {
    @Test
    fun hideAppIconPreferenceTracksTheLauncherAliasAndCanBeRestored() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val packageManager = context.packageManager
        val launcherAlias = ComponentName(context, "rs.chimera.android.MainActivityAlias")
        val wasHidden = AppPreferences.hideAppIcon(context)

        try {
            AppPreferences.updateHideAppIcon(context, true)
            assertTrue(AppPreferences.hideAppIcon(context))
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                packageManager.getComponentEnabledSetting(launcherAlias),
            )
            assertFalse(isLauncherVisible(packageManager, context.packageName, launcherAlias))

            AppPreferences.updateHideAppIcon(context, false)
            assertFalse(AppPreferences.hideAppIcon(context))
            assertEquals(
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                packageManager.getComponentEnabledSetting(launcherAlias),
            )
            assertTrue(isLauncherVisible(packageManager, context.packageName, launcherAlias))

            listOf(
                "android.provider.Telephony.SECRET_CODE",
                "android.telephony.action.SECRET_CODE",
            ).forEach { action ->
                val recoveryIntent = Intent(action)
                    .setData(Uri.parse("android_secret_code://252746382"))
                    .setPackage(context.packageName)
                assertTrue(
                    "No dialer receiver for $action",
                    packageManager.queryBroadcastReceivers(recoveryIntent, 0)
                        .any { it.activityInfo.name == "rs.chimera.android.DialerReceiver" },
                )
            }
        } finally {
            AppPreferences.updateHideAppIcon(context, wasHidden)
        }
    }

    private fun isLauncherVisible(
        packageManager: PackageManager,
        packageName: String,
        launcherAlias: ComponentName,
    ): Boolean = packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName),
        0,
    ).any { it.activityInfo.name == launcherAlias.className }
}
