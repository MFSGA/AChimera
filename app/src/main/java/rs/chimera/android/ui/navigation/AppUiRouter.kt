package rs.chimera.android.ui.navigation

import android.content.Context
import android.content.Intent
import rs.chimera.android.MainActivity
import rs.chimera.android.ui.metacubex.activity.MetaMainActivity

interface AppUiRouter {
    fun openWatfaq(context: Context)

    fun openMetaCubeX(context: Context)
}

object DefaultAppUiRouter : AppUiRouter {
    override fun openWatfaq(context: Context) {
        context.startActivity(
            MainActivity.intent(context).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
    }

    override fun openMetaCubeX(context: Context) {
        context.startActivity(
            Intent(context, MetaMainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
    }
}
