package rs.chimera.android.ui.metacubex.activity

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.SettingsDesign
import rs.chimera.android.ui.metacubex.design.SettingsHomeDesign

class MetaSettingsHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)

        val design = SettingsHomeDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    SettingsHomeDesign.Request.NavigateBack -> finish()
                    SettingsHomeDesign.Request.OpenApp -> openCategory(SettingsDesign.Category.APP)
                    SettingsHomeDesign.Request.OpenNetwork -> openCategory(SettingsDesign.Category.NETWORK)
                    SettingsHomeDesign.Request.OpenOverride -> showUnsupportedOverride()
                    SettingsHomeDesign.Request.OpenMetaFeatures ->
                        openCategory(SettingsDesign.Category.META_FEATURES)
                }
            }
        }
    }

    private fun openCategory(category: SettingsDesign.Category) {
        startActivity(
            Intent(this, MetaSettingsActivity::class.java)
                .putExtra(SettingsDesign.EXTRA_CATEGORY, category.name),
        )
    }

    private fun showUnsupportedOverride() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_category_override)
            .setMessage(R.string.settings_category_override_unavailable)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
