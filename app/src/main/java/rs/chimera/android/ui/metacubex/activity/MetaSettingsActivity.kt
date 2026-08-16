package rs.chimera.android.ui.metacubex.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.service.PortPreference
import rs.chimera.android.ui.formatRuleDiagnostics
import rs.chimera.android.ui.metacubex.design.SettingsDesign
import rs.chimera.android.ui.navigation.DefaultAppUiRouter
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.AppearancePreference
import rs.chimera.android.ui.preferences.LanguagePreference
import rs.chimera.android.ui.preferences.UiVariant
import rs.chimera.android.util.runCatchingPreservingCancellation

class MetaSettingsActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private lateinit var design: SettingsDesign

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)

        design = SettingsDesign(this)
        setContentView(design.root)
        design.render(loadState())

        lifecycleScope.launch {
            for (request in design.requests) {
                handleRequest(request)
            }
        }
    }

    private suspend fun handleRequest(request: SettingsDesign.Request) {
        when (request) {
            SettingsDesign.Request.NavigateBack -> finish()
            SettingsDesign.Request.EditPorts -> showPortDialog()
            SettingsDesign.Request.OpenLogs -> {
                startActivity(Intent(this, MetaLogsDesignActivity::class.java))
            }
            SettingsDesign.Request.OpenDnsDiagnostics -> showDnsDialog()
            SettingsDesign.Request.OpenRuleDiagnostics -> showRuleDiagnostics()
            SettingsDesign.Request.OpenProxyProviders -> showProxyProviders()
            SettingsDesign.Request.OpenAccessControl -> {
                startActivity(Intent(this, MetaAccessControlActivity::class.java))
            }
            SettingsDesign.Request.ChooseLanguage -> showLanguageDialog()
            SettingsDesign.Request.ChooseAppearance -> showAppearanceDialog()
            SettingsDesign.Request.ChooseUiVariant -> showUiVariantDialog()
            is SettingsDesign.Request.SetAllowLan -> {
                saveSettings(SettingsPatch(allowLan = request.enabled))
            }
            is SettingsDesign.Request.SetFakeIp -> {
                saveSettings(SettingsPatch(fakeIp = request.enabled))
            }
            is SettingsDesign.Request.SetIpv6 -> {
                saveSettings(SettingsPatch(ipv6 = request.enabled))
            }
        }
    }

    private suspend fun saveSettings(patch: SettingsPatch) {
        runCatchingPreservingCancellation { backend.updateSettings(patch) }
            .onSuccess {
                design.render(loadState())
                design.showToast(getString(R.string.cmfa_settings_saved))
            }
            .onFailure { error ->
                design.render(loadState())
                design.showToast(
                    getString(
                        R.string.cmfa_settings_save_failed,
                        error.message ?: getString(R.string.profile_unknown_error),
                    ),
                )
            }
    }

    private fun showPortDialog() {
        val state = loadState()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        val mixedInput = portInput(R.string.settings_mixed_port, state.mixedPort)
        val httpInput = portInput(R.string.settings_http_port, state.httpPort)
        val socksInput = portInput(R.string.settings_socks_port, state.socksPort)
        container.addView(mixedInput)
        container.addView(httpInput)
        container.addView(socksInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.settings_listener_ports)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val mixedPort = parsePort(mixedInput, required = true) ?: return@setOnClickListener
                val httpPort = parsePort(httpInput, required = false)
                if (httpInput.text.isNotBlank() && httpPort == null) return@setOnClickListener
                val socksPort = parsePort(socksInput, required = false)
                if (socksInput.text.isNotBlank() && socksPort == null) return@setOnClickListener

                lifecycleScope.launch {
                    saveSettings(
                        SettingsPatch(
                            mixedPort = mixedPort,
                            httpPort = httpPort,
                            socksPort = socksPort,
                            clearHttpPort = httpInput.text.isBlank(),
                            clearSocksPort = socksInput.text.isBlank(),
                        ),
                    )
                }
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showRuleDiagnostics() {
        lifecycleScope.launch {
            runCatchingPreservingCancellation { backend.listRules() }
                .onSuccess { rules ->
                    val message = formatRuleDiagnostics(
                        rules = rules,
                        totalLabel = getString(R.string.rules_diagnostics_count, rules.size),
                        remainingLabel = { count ->
                            getString(R.string.rules_diagnostics_more, count)
                        },
                    )
                    AlertDialog.Builder(this@MetaSettingsActivity)
                        .setTitle(R.string.rules_diagnostics_title)
                        .setMessage(message)
                        .setNegativeButton(R.string.rules_diagnostics_refresh) { _, _ ->
                            showRuleDiagnostics()
                        }.setPositiveButton(android.R.string.ok, null)
                        .show()
                }.onFailure { error ->
                    AlertDialog.Builder(this@MetaSettingsActivity)
                        .setTitle(R.string.rules_diagnostics_title)
                        .setMessage(error.message ?: getString(R.string.profile_unknown_error))
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
        }
    }

    private fun showProxyProviders() {
        lifecycleScope.launch {
            runCatchingPreservingCancellation { backend.listProxyProviders() }
                .onSuccess { providers ->
                    if (providers.isEmpty()) {
                        design.showToast(getString(R.string.proxy_providers_empty))
                        return@onSuccess
                    }
                    AlertDialog.Builder(this@MetaSettingsActivity)
                        .setTitle(R.string.proxy_providers_title)
                        .setItems(
                            providers.map { provider ->
                                getString(
                                    R.string.proxy_provider_summary,
                                    provider.name,
                                    provider.type,
                                    provider.vehicleType,
                                    provider.proxyCount,
                                )
                            }.toTypedArray(),
                        ) { _, which ->
                            showProxyProviderActions(providers[which].name)
                        }.setNegativeButton(android.R.string.cancel, null)
                        .show()
                }.onFailure { error ->
                    design.showToast(error.message ?: getString(R.string.profile_unknown_error))
                }
        }
    }

    private fun showProxyProviderActions(name: String) {
        val actions = arrayOf(
            getString(R.string.proxy_provider_update),
            getString(R.string.proxy_provider_healthcheck),
        )
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(actions) { _, which ->
                lifecycleScope.launch {
                    val result = runCatchingPreservingCancellation {
                        if (which == 0) {
                            backend.updateProxyProvider(name)
                        } else {
                            backend.healthcheckProxyProvider(name)
                        }
                    }
                    result.onSuccess {
                        design.showToast(
                            getString(
                                if (which == 0) {
                                    R.string.proxy_provider_updated
                                } else {
                                    R.string.proxy_provider_checked
                                },
                                name,
                            ),
                        )
                    }.onFailure { error ->
                        design.showToast(
                            getString(
                                R.string.proxy_provider_action_failed,
                                error.message ?: getString(R.string.profile_unknown_error),
                            ),
                        )
                    }
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDnsDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, 0, padding, 0)
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.dns_query_name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(DEFAULT_DNS_QUERY_NAME)
            setSelectAllOnFocus(true)
        }
        val typeInput = EditText(this).apply {
            hint = getString(R.string.dns_record_type)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setText(DEFAULT_DNS_RECORD_TYPE)
            setSelectAllOnFocus(true)
        }
        container.addView(nameInput)
        container.addView(typeInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dns_diagnostics_title)
            .setMessage(R.string.dns_diagnostics_summary)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dns_query_action, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val recordType = typeInput.text.toString().trim().uppercase()
                if (name.isEmpty()) {
                    nameInput.error = getString(R.string.dns_query_name_required)
                    return@setOnClickListener
                }
                if (recordType.isEmpty()) {
                    typeInput.error = getString(R.string.dns_record_type_required)
                    return@setOnClickListener
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                lifecycleScope.launch {
                    runCatchingPreservingCancellation { backend.queryDns(name, recordType) }
                        .onSuccess { result ->
                            dialog.dismiss()
                            AlertDialog.Builder(this@MetaSettingsActivity)
                                .setTitle(R.string.dns_diagnostics_result)
                                .setMessage(result)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }.onFailure { error ->
                            typeInput.error =
                                error.message ?: getString(R.string.profile_unknown_error)
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        }
                }
            }
        }
        dialog.show()
    }

    private fun showLanguageDialog() {
        showChoiceDialog(
            titleRes = R.string.settings_language,
            values = LanguagePreference.entries,
            current = AppPreferences.language(this),
            label = ::languageLabel,
        ) { preference ->
            AppPreferences.updateLanguage(this, preference)
        }
    }

    private fun showAppearanceDialog() {
        showChoiceDialog(
            titleRes = R.string.settings_appearance,
            values = AppearancePreference.entries,
            current = AppPreferences.appearance(this),
            label = ::appearanceLabel,
        ) { preference ->
            AppPreferences.updateAppearance(this, preference)
        }
    }

    private fun showUiVariantDialog() {
        showChoiceDialog(
            titleRes = R.string.settings_ui_style,
            values = UiVariant.entries,
            current = AppPreferences.uiVariant(this),
            label = ::uiVariantLabel,
        ) { variant ->
            when (variant) {
                UiVariant.WATFAQ -> DefaultAppUiRouter.openWatfaq(this)
                UiVariant.METACUBEX -> {
                    AppPreferences.updateUiVariant(this, variant)
                    design.render(loadState())
                }
            }
        }
    }

    private fun <T> showChoiceDialog(
        titleRes: Int,
        values: List<T>,
        current: T,
        label: (T) -> String,
        onSelected: (T) -> Unit,
    ) {
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setSingleChoiceItems(
                values.map(label).toTypedArray(),
                values.indexOf(current),
            ) { dialog, which ->
                dialog.dismiss()
                values[which].takeIf { it != current }?.let(onSelected)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun languageLabel(preference: LanguagePreference): String =
        getString(
            when (preference) {
                LanguagePreference.SYSTEM -> R.string.language_system
                LanguagePreference.SIMPLIFIED_CHINESE -> R.string.language_simplified_chinese
                LanguagePreference.ENGLISH -> R.string.language_english
            },
        )

    private fun appearanceLabel(preference: AppearancePreference): String =
        getString(
            when (preference) {
                AppearancePreference.SYSTEM -> R.string.dark_mode_system
                AppearancePreference.LIGHT -> R.string.dark_mode_light
                AppearancePreference.DARK -> R.string.dark_mode_dark
            },
        )

    private fun uiVariantLabel(variant: UiVariant): String =
        getString(
            when (variant) {
                UiVariant.WATFAQ -> R.string.ui_style_watfaq
                UiVariant.METACUBEX -> R.string.ui_style_metacubex
            },
        )

    private fun portInput(labelRes: Int, value: Int?): EditText =
        EditText(this).apply {
            hint = getString(labelRes)
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(value?.toString().orEmpty())
            setSelectAllOnFocus(true)
        }

    private fun parsePort(input: EditText, required: Boolean): UShort? {
        if (!required && input.text.isBlank()) return null
        return PortPreference.parse(input.text.toString()) ?: run {
            input.error = getString(R.string.settings_port_invalid)
            null
        }
    }

    private fun loadState(): SettingsDesign.State =
        SettingsDesign.State(
            allowLan = prefs.getBoolean("allow_lan", false),
            fakeIp = prefs.getBoolean("fake_ip", false),
            ipv6 = prefs.getBoolean("ipv6", false),
            mixedPort = readPort("mixed_port") ?: DEFAULT_MIXED_PORT,
            httpPort = readPort("http_port"),
            socksPort = readPort("socks_port"),
            language = languageLabel(AppPreferences.language(this)),
            appearance = appearanceLabel(AppPreferences.appearance(this)),
            uiVariant = uiVariantLabel(AppPreferences.uiVariant(this)),
        )

    private fun readPort(key: String): Int? = PortPreference.parse(prefs.all[key])?.toInt()

    private companion object {
        const val DEFAULT_MIXED_PORT = 7890
        const val DEFAULT_DNS_QUERY_NAME = "example.com"
        const val DEFAULT_DNS_RECORD_TYPE = "A"
    }
}
