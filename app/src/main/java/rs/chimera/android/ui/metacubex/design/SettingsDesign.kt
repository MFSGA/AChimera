package rs.chimera.android.ui.metacubex.design

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.applySystemBarInsets
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class SettingsDesign(
    context: Context,
    private val category: Category,
) : Design<SettingsDesign.Request>(context) {
    enum class Category(val viewTag: String, val titleRes: Int) {
        APP("app", R.string.settings_category_app),
        NETWORK("network", R.string.settings_category_network),
        META_FEATURES("meta_features", R.string.settings_category_meta_features),
    }

    companion object {
        const val EXTRA_CATEGORY = "rs.chimera.android.ui.metacubex.settings_category"
    }

    sealed class Request {
        data object NavigateBack : Request()
        data object EditPorts : Request()
        data object OpenLogs : Request()
        data object ShareDiagnostics : Request()
        data object OpenDnsDiagnostics : Request()
        data object OpenRuleDiagnostics : Request()
        data object OpenProxyProviders : Request()
        data object OpenAccessControl : Request()
        data object ResetNetwork : Request()
        data object ResetRuntimeSettings : Request()
        data object ChooseLanguage : Request()
        data object ChooseAppearance : Request()
        data object ChooseUiVariant : Request()
        data class SetAutoRestart(val enabled: Boolean) : Request()
        data class SetAllowLan(val enabled: Boolean) : Request()
        data class SetFakeIp(val enabled: Boolean) : Request()
        data class SetIpv6(val enabled: Boolean) : Request()
    }

    data class State(
        val allowLan: Boolean,
        val fakeIp: Boolean,
        val ipv6: Boolean,
        val mixedPort: Int,
        val httpPort: Int?,
        val socksPort: Int?,
        val language: String,
        val appearance: String,
        val uiVariant: String,
        val autoRestart: Boolean,
        val vpnSystemStatus: String,
        val vpnSystemRestricted: Boolean,
    )

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_settings,
        context.root,
        false,
    )

    private val toolbarNavigation = root.findViewById<View>(R.id.toolbar_navigation)
    private val toolbar = root.findViewById<View>(R.id.toolbar)
    private val toolbarTitle = root.findViewById<TextView>(R.id.toolbar_title)
    private val settingsScroll = root.findViewById<ScrollView>(R.id.settings_scroll)
    private val cardLanguage = root.findViewById<View>(R.id.card_language)
    private val cardAppearance = root.findViewById<View>(R.id.card_appearance)
    private val cardUiVariant = root.findViewById<View>(R.id.card_ui_variant)
    private val rowAllowLan = root.findViewById<View>(R.id.row_allow_lan)
    private val rowFakeIp = root.findViewById<View>(R.id.row_fake_ip)
    private val rowIpv6 = root.findViewById<View>(R.id.row_ipv6)
    private val cardPorts = root.findViewById<View>(R.id.card_ports)
    private val cardLogs = root.findViewById<MaterialCardView>(R.id.card_logs)
    private val cardDiagnostics = root.findViewById<MaterialCardView>(R.id.card_diagnostics)
    private val cardDns = root.findViewById<MaterialCardView>(R.id.card_dns)
    private val cardRules = root.findViewById<MaterialCardView>(R.id.card_rules)
    private val cardProviders = root.findViewById<MaterialCardView>(R.id.card_providers)
    private val cardAccessControl = root.findViewById<View>(R.id.card_access_control)
    private val cardResetNetwork = root.findViewById<View>(R.id.card_reset_network)
    private val cardResetSettings = root.findViewById<View>(R.id.card_reset_settings)
    private val rowAutoRestart = root.findViewById<View>(R.id.row_auto_restart)
    private val switchAutoRestart = root.findViewById<SwitchMaterial>(R.id.switch_auto_restart)
    private val switchAllowLan = root.findViewById<SwitchMaterial>(R.id.switch_allow_lan)
    private val switchFakeIp = root.findViewById<SwitchMaterial>(R.id.switch_fake_ip)
    private val switchIpv6 = root.findViewById<SwitchMaterial>(R.id.switch_ipv6)
    private val languageSummary = root.findViewById<TextView>(R.id.text_language_summary)
    private val appearanceSummary = root.findViewById<TextView>(R.id.text_appearance_summary)
    private val uiVariantSummary = root.findViewById<TextView>(R.id.text_ui_variant_summary)
    private val portsSummary = root.findViewById<TextView>(R.id.text_ports_summary)
    private val vpnSystemSummary = root.findViewById<TextView>(R.id.text_vpn_system_summary)
    private val vpnSystemDefaultTextColor = vpnSystemSummary.currentTextColor
    private var rendering = false
    private var appBarElevated = false
    private var appBarElevationAnimator: ValueAnimator? = null

    init {
        root.applySystemBarInsets()

        toolbarTitle.text = context.getString(category.titleRes)
        root.findViewById<LinearLayout>(R.id.settings_content).let { content ->
            for (index in 0 until content.childCount) {
                val child = content.getChildAt(index)
                child.visibility = if (child.tag == category.viewTag) View.VISIBLE else View.GONE
            }
        }

        toolbarNavigation.setOnClickListener { request(Request.NavigateBack) }
        settingsScroll.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val elevated = scrollY > 0
            if (appBarElevated != elevated) {
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
        }
        cardLanguage.setOnClickListener { request(Request.ChooseLanguage) }
        cardAppearance.setOnClickListener { request(Request.ChooseAppearance) }
        cardUiVariant.setOnClickListener { request(Request.ChooseUiVariant) }
        rowAutoRestart.setOnClickListener { switchAutoRestart.isChecked = !switchAutoRestart.isChecked }
        rowAllowLan.setOnClickListener { switchAllowLan.isChecked = !switchAllowLan.isChecked }
        rowFakeIp.setOnClickListener { switchFakeIp.isChecked = !switchFakeIp.isChecked }
        rowIpv6.setOnClickListener { switchIpv6.isChecked = !switchIpv6.isChecked }
        cardPorts.setOnClickListener { request(Request.EditPorts) }
        cardLogs.setOnClickListener { request(Request.OpenLogs) }
        cardDiagnostics.setOnClickListener { request(Request.ShareDiagnostics) }
        cardDns.setOnClickListener { request(Request.OpenDnsDiagnostics) }
        cardRules.setOnClickListener { request(Request.OpenRuleDiagnostics) }
        cardProviders.setOnClickListener { request(Request.OpenProxyProviders) }
        cardAccessControl.setOnClickListener { request(Request.OpenAccessControl) }
        cardResetNetwork.setOnClickListener {
            cardResetNetwork.isEnabled = false
            request(Request.ResetNetwork)
        }
        cardResetSettings.setOnClickListener { request(Request.ResetRuntimeSettings) }
        switchAllowLan.setOnCheckedChangeListener { _, checked ->
            if (!rendering) request(Request.SetAllowLan(checked))
        }
        switchAutoRestart.setOnCheckedChangeListener { _, checked ->
            if (!rendering) request(Request.SetAutoRestart(checked))
        }
        switchFakeIp.setOnCheckedChangeListener { _, checked ->
            if (!rendering) request(Request.SetFakeIp(checked))
        }
        switchIpv6.setOnCheckedChangeListener { _, checked ->
            if (!rendering) request(Request.SetIpv6(checked))
        }
    }

    fun setNetworkResetInProgress(inProgress: Boolean) {
        cardResetNetwork.isEnabled = !inProgress
    }
    fun render(state: State) {
        rendering = true
        switchAutoRestart.isChecked = state.autoRestart
        switchAllowLan.isChecked = state.allowLan
        switchFakeIp.isChecked = state.fakeIp
        switchIpv6.isChecked = state.ipv6
        languageSummary.text = state.language
        appearanceSummary.text = state.appearance
        uiVariantSummary.text = state.uiVariant
        portsSummary.text = context.getString(
            R.string.cmfa_ports_summary,
            state.mixedPort,
            state.httpPort?.toString() ?: context.getString(R.string.none),
            state.socksPort?.toString() ?: context.getString(R.string.none),
        )
        vpnSystemSummary.text = state.vpnSystemStatus
        vpnSystemSummary.setTextColor(
            if (state.vpnSystemRestricted) {
                context.getColor(R.color.clash_error)
            } else {
                vpnSystemDefaultTextColor
            },
        )
        rendering = false
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }
}
