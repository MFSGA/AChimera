package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class MetaNewProfileDesign(context: Context) : Design<MetaNewProfileDesign.Request>(context) {
    enum class Request {
        NavigateBack,
        ImportFile,
        ImportUrl,
        ImportQrCode,
    }

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_new_profile,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<View>(R.id.toolbar)
    private val scrollRoot = root.findViewById<ScrollView>(R.id.new_profile_scroll)
    private var appBarElevated = false
    private var appBarElevationAnimator: ValueAnimator? = null

    init {
        root.applySystemBarInsets()
        root.findViewById<TextView>(R.id.toolbar_title).setText(R.string.profile_create)
        root.findViewById<View>(R.id.toolbar_navigation)
            .setOnClickListener { request(Request.NavigateBack) }
        root.findViewById<View>(R.id.provider_file)
            .setOnClickListener { request(Request.ImportFile) }
        root.findViewById<View>(R.id.provider_url)
            .setOnClickListener { request(Request.ImportUrl) }
        root.findViewById<View>(R.id.provider_qr_code)
            .setOnClickListener { request(Request.ImportQrCode) }
        scrollRoot.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            setToolbarElevated(scrollY > 0)
        }
    }

    private fun setToolbarElevated(elevated: Boolean) {
        if (appBarElevated == elevated) return

        appBarElevated = elevated
        appBarElevationAnimator?.cancel()
        val targetElevation = if (elevated) {
            5 * context.resources.displayMetrics.density
        } else {
            0f
        }
        appBarElevationAnimator = ValueAnimator.ofFloat(toolbar.elevation, targetElevation).apply {
            duration = 300L
            addUpdateListener { toolbar.elevation = it.animatedValue as Float }
            start()
        }
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }
}
