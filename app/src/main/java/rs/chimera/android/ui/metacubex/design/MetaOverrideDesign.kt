package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class MetaOverrideDesign(context: Context) : Design<MetaOverrideDesign.Request>(context) {
    enum class Request {
        NavigateBack,
    }

    private data class Section(
        @param:StringRes val title: Int,
        val rows: List<Int>,
    )

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_override,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<View>(R.id.toolbar)
    private val content = root.findViewById<LinearLayout>(R.id.override_content)
    private val scroll = root.findViewById<ScrollView>(R.id.override_scroll)
    private var elevated = false
    private var elevationAnimator: ValueAnimator? = null

    init {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val horizontalInsets = view.landscapeSystemBarInsets(bars)
            view.setPaddingRelative(
                horizontalInsets.start,
                0,
                horizontalInsets.end,
                0,
            )
            toolbar.setPadding(0, bars.top, 0, 0)
            content.setPadding(
                content.paddingLeft,
                bars.top + context.resources.getDimensionPixelSize(R.dimen.metacubex_toolbar_height),
                content.paddingRight,
                bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
        root.findViewById<View>(R.id.toolbar_navigation)
            .setOnClickListener { request(Request.NavigateBack) }
        root.findViewById<View>(R.id.toolbar_reset).apply {
            isEnabled = false
            isClickable = false
            isFocusable = false
            alpha = DISABLED_ALPHA
        }
        scroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val shouldElevate = scrollY > 0
            if (elevated != shouldElevate) {
                elevated = shouldElevate
                elevationAnimator?.cancel()
                val target = if (shouldElevate) {
                    5 * context.resources.displayMetrics.density
                } else {
                    0f
                }
                elevationAnimator = ValueAnimator.ofFloat(toolbar.elevation, target).apply {
                    duration = 300L
                    addUpdateListener { toolbar.elevation = it.animatedValue as Float }
                    start()
                }
            }
        }

        content.addView(createUnavailableNotice())
        sections.forEach { section ->
            content.addView(createCategory(section.title))
            section.rows.forEach { row -> content.addView(createRow(row)) }
        }
    }

    private fun createUnavailableNotice(): TextView = TextView(context).apply {
        setText(R.string.settings_category_override_unavailable)
        setTextAppearance(R.style.TextAppearance_MaterialComponents_Body2)
        setPaddingRelative(dp(65), dp(8), dp(17.5f), dp(8))
    }

    private fun createCategory(@StringRes title: Int): View = (context.layoutInflater.inflate(
        R.layout.meta_feature_preference_category,
        content,
        false,
    ) as TextView).apply {
        setText(title)
    }

    private fun createRow(@StringRes title: Int): View = context.layoutInflater.inflate(
        R.layout.meta_feature_preference_item,
        content,
        false,
    ).apply {
        findViewById<TextView>(R.id.meta_feature_title).setText(title)
        findViewById<TextView>(R.id.meta_feature_summary)
            .setText(R.string.meta_override_unavailable)
        isEnabled = false
        isClickable = false
        isFocusable = false
        alpha = DISABLED_ALPHA
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun dp(value: Float): Int = (value * context.resources.displayMetrics.density).toInt()

    private fun request(request: Request) {
        requests.trySend(request)
    }

    private companion object {
        const val DISABLED_ALPHA = 0.33f

        val sections = listOf(
            Section(
                R.string.meta_override_general,
                listOf(
                    R.string.meta_override_http_port,
                    R.string.meta_override_socks_port,
                    R.string.meta_override_redirect_port,
                    R.string.meta_override_tproxy_port,
                    R.string.meta_override_mixed_port,
                    R.string.meta_override_authentication,
                    R.string.meta_override_allow_lan,
                    R.string.meta_override_ipv6,
                    R.string.meta_override_bind_address,
                    R.string.meta_override_external_controller,
                    R.string.meta_override_external_controller_tls,
                    R.string.meta_override_allow_origins,
                    R.string.meta_override_allow_private_network,
                    R.string.meta_override_secret,
                    R.string.meta_override_mode,
                    R.string.meta_override_log_level,
                    R.string.meta_override_hosts,
                ),
            ),
            Section(
                R.string.meta_override_dns,
                listOf(
                    R.string.meta_override_strategy,
                    R.string.meta_override_prefer_h3,
                    R.string.meta_override_listen,
                    R.string.meta_override_append_system_dns,
                    R.string.meta_override_ipv6,
                    R.string.meta_override_use_hosts,
                    R.string.meta_override_enhanced_mode,
                    R.string.meta_override_name_server,
                    R.string.meta_override_fallback,
                    R.string.meta_override_default_name_server,
                    R.string.meta_override_fakeip_filter,
                    R.string.meta_override_fakeip_filter_mode,
                    R.string.meta_override_geoip_fallback,
                    R.string.meta_override_geoip_fallback_code,
                    R.string.meta_override_domain_fallback,
                    R.string.meta_override_ipcidr_fallback,
                    R.string.meta_override_name_server_policy,
                ),
            ),
        )
    }
}
