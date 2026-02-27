package rs.chimera.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import rs.chimera.android.theme.ChimeraTheme
import rs.chimera.android.ui.ChimeraApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Global.init(application)
        setContent {
            ChimeraTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    ChimeraApp()
                }
            }
        }
    }
}
