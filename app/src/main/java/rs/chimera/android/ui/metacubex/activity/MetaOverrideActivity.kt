package rs.chimera.android.ui.metacubex.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.MetaOverrideDesign

class MetaOverrideActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)

        val design = MetaOverrideDesign(this)
        setContentView(design.root)
        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    MetaOverrideDesign.Request.NavigateBack -> finish()
                }
            }
        }
    }
}
