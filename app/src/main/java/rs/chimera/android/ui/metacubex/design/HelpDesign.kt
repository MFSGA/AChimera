package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class HelpDesign(context: Context) : Design<HelpDesign.Request>(context) {
    sealed interface Request {
        data object NavigateBack : Request
        data object OpenOnboarding : Request
        data class OpenLink(val uri: Uri) : Request
    }

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_help,
        context.root,
        false,
    )

    init {
        root.applySystemBarInsets()
        root.findViewById<View>(R.id.toolbar_navigation)
            .setOnClickListener { request(Request.NavigateBack) }
        root.findViewById<TextView>(R.id.help_tips).text = HtmlCompat.fromHtml(
            context.getString(R.string.meta_help_tips),
            HtmlCompat.FROM_HTML_MODE_LEGACY,
        )
        bindLink(R.id.help_clash_wiki, R.string.meta_help_clash_wiki_url)
        bindLink(R.id.help_clash_meta_wiki, R.string.meta_help_clash_meta_wiki_url)
        bindLink(R.id.help_clash_meta_core, R.string.meta_help_clash_meta_core_url)
        bindLink(
            R.id.help_clash_meta_for_android,
            R.string.meta_help_clash_meta_for_android_url,
        )
        root.findViewById<View>(R.id.help_onboarding).setOnClickListener {
            request(Request.OpenOnboarding)
        }
    }

    private fun bindLink(viewId: Int, urlRes: Int) {
        root.findViewById<View>(viewId).setOnClickListener {
            request(Request.OpenLink(Uri.parse(context.getString(urlRes))))
        }
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }
}
