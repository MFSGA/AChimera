package rs.chimera.android.ui.metacubex.design

import android.content.Context
import android.view.View
import android.widget.TextView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import rs.chimera.android.R
import rs.chimera.android.ui.metacubex.design.util.layoutInflater
import rs.chimera.android.ui.metacubex.design.util.root

class SettingsDesign(context: Context) : Design<SettingsDesign.Request>(context) {
    sealed class Request {
        data object NavigateBack : Request()
        data object EditPorts : Request()
        data object OpenLogs : Request()
        data object OpenDnsDiagnostics : Request()
        data object OpenRuleDiagnostics : Request()
        data object OpenProxyProviders : Request()
        data object OpenAccessControl : Request()
        data object ChooseLanguage : Request()
        data object ChooseAppearance : Request()
        data object ChooseUiVariant : Request()
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
    )

    override val root: View = context.layoutInflater.inflate(
        R.layout.meta_design_settings,
        context.root,
        false,
    )

    private val toolbar = root.findViewById<MaterialToolbar>(R.id.toolbar)
    private val cardLanguage = root.findViewById<MaterialCardView>(R.id.card_language)
    private val cardAppearance = root.findViewById<MaterialCardView>(R.id.card_appearance)
    private val cardUiVariant = root.findViewById<MaterialCardView>(R.id.card_ui_variant)
    private val cardPorts = root.findViewById<MaterialCardView>(R.id.card_ports)
    private val cardLogs = root.findViewById<MaterialCardView>(R.id.card_logs)
    private val cardDns = root.findViewById<MaterialCardView>(R.id.card_dns)
    private val cardRules = root.findViewById<MaterialCardView>(R.id.card_rules)
    private val cardProviders = root.findViewById<MaterialCardView>(R.id.card_providers)
    private val cardAccessControl = root.findViewById<MaterialCardView>(R.id.card_access_control)
    private val switchAllowLan = root.findViewById<SwitchMaterial>(R.id.switch_allow_lan)
    private val switchFakeIp = root.findViewById<SwitchMaterial>(R.id.switch_fake_ip)
    private val switchIpv6 = root.findViewById<SwitchMaterial>(R.id.switch_ipv6)
    private val languageSummary = root.findViewById<TextView>(R.id.text_language_summary)
    private val appearanceSummary = root.findViewById<TextView>(R.id.text_appearance_summary)
    private val uiVariantSummary = root.findViewById<TextView>(R.id.text_ui_variant_summary)
    private val portsSummary = root.findViewById<TextView>(R.id.text_ports_summary)
    private var rendering = false

    init {
        toolbar.setNavigationOnClickListener { request(Request.NavigateBack) }
        cardLanguage.setOnClickListener { request(Request.ChooseLanguage) }
        cardAppearance.setOnClickListener { request(Request.ChooseAppearance) }
        cardUiVariant.setOnClickListener { request(Request.ChooseUiVariant) }
        cardPorts.setOnClickListener { request(Request.EditPorts) }
        cardLogs.setOnClickListener { request(Request.OpenLogs) }
        cardDns.setOnClickListener { request(Request.OpenDnsDiagnostics) }
        cardRules.setOnClickListener { request(Request.OpenRuleDiagnostics) }
        cardProviders.setOnClickListener { request(Request.OpenProxyProviders) }
        cardAccessControl.setOnClickListener { request(Request.OpenAccessControl) }
        switchAllowLan.setOnCheckedChangeListener { _, checked ->
            if (!rendering) request(Request.SetAllowLan(checked))
        }
        switchFakeIp.setOnCheckedChangeListener { _, checked ->
            if (!rendering) request(Request.SetFakeIp(checked))
        }
        switchIpv6.setOnCheckedChangeListener { _, checked ->
            if (!rendering) request(Request.SetIpv6(checked))
        }
    }

    fun render(state: State) {
        rendering = true
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
        rendering = false
    }

    private fun request(request: Request) {
        requests.trySend(request)
    }
}
