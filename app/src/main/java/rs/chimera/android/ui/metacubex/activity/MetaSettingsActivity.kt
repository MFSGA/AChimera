package rs.chimera.android.ui.metacubex.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.activity.compose.setContent
import rs.chimera.android.R
import rs.chimera.android.theme.ChimeraTheme
import rs.chimera.android.ui.SettingsScreen
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class MetaSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)

        setContent {
            ChimeraTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(onLogsClick = {
                        startActivity(android.content.Intent(this, MetaLogsActivity::class.java))
                    })
                }
            }
        }
    }
}
