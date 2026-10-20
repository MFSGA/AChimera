package rs.chimera.android.ui.metacubex.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.MetaNewProfileDesign

class MetaNewProfileActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_SOURCE = "rs.chimera.android.ui.metacubex.profile_source"
        const val SOURCE_FILE = "file"
        const val SOURCE_URL = "url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)

        val design = MetaNewProfileDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    MetaNewProfileDesign.Request.NavigateBack -> finish()
                    MetaNewProfileDesign.Request.ImportFile -> returnSource(SOURCE_FILE)
                    MetaNewProfileDesign.Request.ImportUrl -> returnSource(SOURCE_URL)
                }
            }
        }
    }

    private fun returnSource(source: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_SOURCE, source))
        finish()
    }
}
