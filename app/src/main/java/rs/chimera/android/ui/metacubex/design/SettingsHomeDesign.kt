package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.ScrollView
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class SettingsHomeDesign(context: Context) : Design<SettingsHomeDesign.Request>(context) {
    enum class Request {
        NavigateBack,
        OpenApp,
        OpenNetwork,
        OpenOverride,
        OpenMetaFeatures,
    }

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_settings_home,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<View>(R.id.toolbar)
    private val settingsScroll = root.findViewById<ScrollView>(R.id.settings_scroll)
    private var appBarElevated = false
    private var appBarElevationAnimator: ValueAnimator? = null

    init {
        root.applySystemBarInsets()
        settingsScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val elevated = scrollY > 0
            if (appBarElevated != elevated) {
                appBarElevated = elevated
                appBarElevationAnimator?.end()
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
        }
        root.findViewById<View>(R.id.toolbar_navigation)
            .setOnClickListener { request(Request.NavigateBack) }
        root.findViewById<View>(R.id.category_app).setOnClickListener { request(Request.OpenApp) }
        root.findViewById<View>(R.id.category_network)
            .setOnClickListener { request(Request.OpenNetwork) }
        root.findViewById<View>(R.id.category_override)
            .setOnClickListener { request(Request.OpenOverride) }
        root.findViewById<View>(R.id.category_meta_features)
            .setOnClickListener { request(Request.OpenMetaFeatures) }
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }
}
