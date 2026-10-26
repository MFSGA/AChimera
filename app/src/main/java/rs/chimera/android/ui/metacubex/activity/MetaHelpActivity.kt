package rs.chimera.android.ui.metacubex.activity

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.ui.onboarding.OnboardingActivity
import rs.chimera.android.ui.metacubex.design.HelpDesign

class MetaHelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val design = HelpDesign(this)
        setContentView(design.root)

        lifecycleScope.launch {
            for (request in design.requests) {
                when (request) {
                    HelpDesign.Request.NavigateBack -> finish()
                    HelpDesign.Request.OpenOnboarding -> {
                        startActivity(OnboardingActivity.intent(this@MetaHelpActivity))
                    }
                    is HelpDesign.Request.OpenLink -> openLink(request.uri)
                }
            }
        }
    }

    private fun openLink(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.meta_help_browser_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
