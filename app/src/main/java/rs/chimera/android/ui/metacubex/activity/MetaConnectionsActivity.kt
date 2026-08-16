package rs.chimera.android.ui.metacubex.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import rs.chimera.android.theme.ChimeraTheme
import rs.chimera.android.ui.ConnectionsScreen

class MetaConnectionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChimeraTheme {
                ConnectionsScreen(onBack = ::finish)
            }
        }
    }
}
