package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class MetaNewProfileDesign(context: Context) : Design<MetaNewProfileDesign.Request>(context) {
    data class ExternalProvider(
        val packageName: String,
        val name: String,
        val summary: String,
        val icon: Drawable,
        val componentName: ComponentName,
    )

    sealed class Request {
        data object NavigateBack : Request()
        data object ImportFile : Request()
        data object ImportUrl : Request()
        data object ImportQrCode : Request()
        data class OpenExternalProvider(val componentName: ComponentName) : Request()
        data class OpenExternalProviderDetails(val packageName: String) : Request()
    }

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_new_profile,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<View>(R.id.toolbar)
    private val scrollRoot = root.findViewById<ScrollView>(R.id.new_profile_scroll)
    private val externalProviders = root.findViewById<LinearLayout>(R.id.external_profile_providers)
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

    fun setExternalProviders(providers: List<ExternalProvider>) {
        externalProviders.removeAllViews()
        providers.forEach { provider ->
            val item = context.layoutInflater.inflate(
                R.layout.meta_adapter_profile_provider,
                externalProviders,
                false,
            )
            item.findViewById<ImageView>(R.id.provider_icon).setImageDrawable(provider.icon)
            item.findViewById<TextView>(R.id.provider_name).text = provider.name
            item.findViewById<TextView>(R.id.provider_summary).text = provider.summary
            item.contentDescription = listOf(provider.name, provider.summary)
                .filter(String::isNotBlank)
                .joinToString(", ")
            item.setOnClickListener {
                request(Request.OpenExternalProvider(provider.componentName))
            }
            item.setOnLongClickListener {
                request(Request.OpenExternalProviderDetails(provider.packageName))
                true
            }
            externalProviders.addView(item)
        }
        externalProviders.visibility = if (providers.isEmpty()) View.GONE else View.VISIBLE
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
