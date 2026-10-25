package rs.chimera.android.ui.navigation

import android.content.Context
import android.content.Intent
import rs.chimera.android.MainActivity
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.UiVariant

interface AppUiRouter {
    fun openWatfaq(context: Context): Boolean

    fun openMetaCubeX(context: Context): Boolean
}

object DefaultAppUiRouter : AppUiRouter {
    override fun openWatfaq(context: Context): Boolean =
        VpnUiActionGate.runIfUiSwitchAllowed {
            AppPreferences.updateUiVariant(context, UiVariant.WATFAQ)
            launchAsRoot(context, MainActivity.intent(context))
        }

    override fun openMetaCubeX(context: Context): Boolean =
        VpnUiActionGate.runIfUiSwitchAllowed {
            AppPreferences.updateUiVariant(context, UiVariant.METACUBEX)
            launchAsRoot(context, Intent(context, MetaMainActivity::class.java))
        }

    private fun launchAsRoot(context: Context, intent: Intent) {
        context.startActivity(
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }
}
