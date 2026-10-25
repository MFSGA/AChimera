package rs.chimera.android.ui.metacubex.activity

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import rs.chimera.android.R
import rs.chimera.android.service.PortPreference
import rs.chimera.android.backend.BackendProvider
import rs.chimera.android.backend.DiagnosticsShareCoordinator
import rs.chimera.android.backend.VpnSettingsAvailabilityPolicy
import rs.chimera.android.backend.model.SettingsDefaults
import rs.chimera.android.backend.model.SettingsPatch
import rs.chimera.android.ui.format
import rs.chimera.android.ui.formatRuleDiagnostics
import rs.chimera.android.ui.metacubex.design.SettingsDesign
import rs.chimera.android.ui.resolvePresentation
import rs.chimera.android.ui.navigation.DefaultAppUiRouter
import rs.chimera.android.ui.navigation.VpnUiActionGate
import rs.chimera.android.ui.preferences.AppRecentTasksVisibility
import rs.chimera.android.ui.preferences.AppPreferences
import rs.chimera.android.ui.preferences.AppearancePreference
import rs.chimera.android.ui.preferences.LanguagePreference
import rs.chimera.android.ui.preferences.UiVariant
import rs.chimera.android.viewmodel.AppFilterMode
import java.util.Locale
import rs.chimera.android.util.runCatchingPreservingCancellation
import rs.chimera.android.util.toUserVisibleMessage

class MetaSettingsActivity : AppCompatActivity() {
    private val backend = BackendProvider.provide()
    private lateinit var design: SettingsDesign
    private var optionsUnavailableSnackbar: Snackbar? = null
    private var optionsUnavailableShown = false
    private val category: SettingsDesign.Category by lazy {
        SettingsDesign.Category.values().firstOrNull {
            it.name == intent.getStringExtra(SettingsDesign.EXTRA_CATEGORY)
        } ?: SettingsDesign.Category.APP
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Chimera_MetaCubeX)

        design = SettingsDesign(this, category)
        setContentView(design.root)
        design.render(loadState())

        lifecycleScope.launch {
            for (request in design.requests) {
                handleRequest(request)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { backend.vpnSystemStatus.collect { design.render(loadState()) } }
                launch { backend.settings.collect { design.render(loadState()) } }
                launch {
                    backend.profileDeletionAvailability.collect { availability ->
                        design.render(loadState())
                        val canChangeSettings = VpnSettingsAvailabilityPolicy.canChangeSettings(
                            availability.serviceState,
                            availability.hasRunRequest,
                        )
                        if (
                            category == SettingsDesign.Category.NETWORK &&
                            !canChangeSettings &&
                            !optionsUnavailableShown
                        ) {
                            optionsUnavailableShown = true
                            optionsUnavailableSnackbar = Snackbar.make(
                                design.root,
                                R.string.cmfa_options_unavailable,
                                Snackbar.LENGTH_INDEFINITE,
                            ).also(Snackbar::show)
                        } else if (
                            category == SettingsDesign.Category.NETWORK &&
                            canChangeSettings
                        ) {
                            optionsUnavailableSnackbar?.dismiss()
                            optionsUnavailableSnackbar = null
                            optionsUnavailableShown = false
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        optionsUnavailableSnackbar?.dismiss()
        optionsUnavailableSnackbar = null
        super.onDestroy()
    }

    private suspend fun handleRequest(request: SettingsDesign.Request) {
        when (request) {
            SettingsDesign.Request.NavigateBack -> finish()
            SettingsDesign.Request.EditPorts -> {
                if (canChangeVpnSettings()) showPortDialog()
            }
            SettingsDesign.Request.OpenLogs -> {
                startActivity(Intent(this, MetaLogsHomeActivity::class.java))
            }
            SettingsDesign.Request.ShareDiagnostics -> shareDiagnostics()
            SettingsDesign.Request.OpenDnsDiagnostics -> showDnsDialog()
            SettingsDesign.Request.OpenRuleDiagnostics -> showRuleDiagnostics()
            SettingsDesign.Request.OpenProxyProviders -> showProxyProviders()
            SettingsDesign.Request.OpenAccessControl -> {
                if (canChangeVpnSettings()) {
                    startActivity(Intent(this, MetaAccessControlActivity::class.java))
                }
            }
            is SettingsDesign.Request.SetAccessControlMode -> {
                if (canChangeVpnSettings()) {
                    saveSettings(
                        SettingsPatch(appFilterMode = request.mode.name),
                        R.string.access_control_mode_saved,
                    )
                }
            }
            SettingsDesign.Request.ResetNetwork -> resetNetwork()
            SettingsDesign.Request.ResetRuntimeSettings -> {
                if (canChangeVpnSettings()) showResetRuntimeSettingsDialog()
            }
            SettingsDesign.Request.ChooseLanguage -> showLanguageDialog()
            SettingsDesign.Request.ChooseAppearance -> showAppearanceDialog()
            SettingsDesign.Request.ChooseUiVariant -> showUiVariantDialog()
            is SettingsDesign.Request.SetAutoRestart -> {
                AppPreferences.updateAutoRestart(this, request.enabled)
                design.render(loadState())
            }
            is SettingsDesign.Request.SetHideAppIcon -> {
                AppPreferences.updateHideAppIcon(this, request.enabled)
                design.render(loadState())
            }
            is SettingsDesign.Request.SetHideFromRecents -> {
                AppPreferences.updateHideFromRecents(this, request.enabled)
                AppRecentTasksVisibility.apply(this, request.enabled)
                window.decorView.post {
                    AppRecentTasksVisibility.apply(this, request.enabled)
                }
                design.render(loadState())
            }
            is SettingsDesign.Request.SetShowTrafficInNotification -> {
                if (canChangeVpnSettings()) {
                    AppPreferences.updateShowTrafficInNotification(this, request.enabled)
                }
                design.render(loadState())
            }
            is SettingsDesign.Request.SetAllowLan -> {
                if (canChangeVpnSettings()) saveSettings(SettingsPatch(allowLan = request.enabled))
            }
            is SettingsDesign.Request.SetFakeIp -> {
                if (canChangeVpnSettings()) saveSettings(SettingsPatch(fakeIp = request.enabled))
            }
            is SettingsDesign.Request.SetIpv6 -> {
                if (canChangeVpnSettings()) saveSettings(SettingsPatch(ipv6 = request.enabled))
            }
        }
    }

    private suspend fun shareDiagnostics() {
        runCatchingPreservingCancellation {
            DiagnosticsShareCoordinator.prepare(this, backend)
        }.onSuccess { shareIntent ->
            runCatching {
                startActivity(
                    Intent.createChooser(
                        shareIntent,
                        getString(R.string.diagnostics_share_chooser),
                    ),
                )
            }.onFailure {
                design.showToast(getString(R.string.diagnostics_share_failed))
            }
        }.onFailure {
            design.showToast(getString(R.string.diagnostics_share_failed))
        }
    }

    private suspend fun saveSettings(
        patch: SettingsPatch,
        successMessage: Int = R.string.cmfa_settings_saved,
    ) {
        if (!canChangeVpnSettings()) {
            design.showToast(getString(R.string.cmfa_options_unavailable), Snackbar.LENGTH_INDEFINITE)
            design.render(loadState())
            return
        }
        runCatchingPreservingCancellation { backend.updateSettings(patch) }
            .onSuccess {
                design.render(loadState())
                design.showToast(getString(successMessage))
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

    private suspend fun resetNetwork() {
        try {
            runCatchingPreservingCancellation { backend.resetNetwork() }
                .onSuccess {
                    design.showToast(getString(R.string.settings_reset_network_success))
                }.onFailure { error ->
                    design.showToast(
                        error.toUserVisibleMessage(this@MetaSettingsActivity, R.string.profile_unknown_error),
                    )
                }
        } finally {
            design.setNetworkResetInProgress(false)
        }
    }

    private fun showResetRuntimeSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_reset_defaults_title)
            .setMessage(R.string.settings_reset_defaults_message)
            .setPositiveButton(R.string.settings_reset_defaults) { _, _ ->
                lifecycleScope.launch { saveSettings(SettingsDefaults.resetPatch()) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
                        totalLabel = resources.getQuantityString(
                            R.plurals.rules_diagnostics_count,
                            rules.size,
                            rules.size,
                        ),
                        remainingLabel = { count ->
                            resources.getQuantityString(
                                R.plurals.rules_diagnostics_more,
                                count,
                                count,
                            )
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
                                resources.getQuantityString(
                                    R.plurals.proxy_provider_summary,
                                    provider.proxyCount,
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
            titleRes = R.string.settings_dark_mode,
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
            if (!VpnUiActionGate.canSwitchUi()) {
                Snackbar.make(
                    design.root,
                    R.string.ui_switch_wait_for_vpn_action,
                    Snackbar.LENGTH_SHORT,
                ).show()
                return@showChoiceDialog
            }
            when (variant) {
                UiVariant.WATFAQ -> {
                    if (!DefaultAppUiRouter.openWatfaq(this)) {
                        Snackbar.make(
                            design.root,
                            R.string.ui_switch_wait_for_vpn_action,
                            Snackbar.LENGTH_SHORT,
                        ).show()
                    }
                }
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
                AppearancePreference.SYSTEM -> R.string.meta_dark_mode_system
                AppearancePreference.LIGHT -> R.string.meta_dark_mode_light
                AppearancePreference.DARK -> R.string.meta_dark_mode_dark
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
            setText(value?.let { String.format(Locale.ROOT, "%d", it) }.orEmpty())
            setSelectAllOnFocus(true)
        }

    private fun parsePort(input: EditText, required: Boolean): UShort? {
        if (!required && input.text.isBlank()) return null
        return PortPreference.parse(input.text.toString()) ?: run {
            input.error = getString(R.string.settings_port_invalid)
            null
        }
    }

    private fun loadState(): SettingsDesign.State {
        val settings = backend.settings.value
        val appFilterMode = runCatching { AppFilterMode.valueOf(settings.appFilterMode) }
            .getOrDefault(AppFilterMode.ALL)
        val vpnPresentation = backend.vpnSystemStatus.value.resolvePresentation()
        return SettingsDesign.State(
            allowLan = settings.allowLan,
            fakeIp = settings.fakeIp,
            ipv6 = settings.ipv6,
            appFilterMode = appFilterMode,
            canChangeVpnSettings = canChangeVpnSettings(),
            mixedPort = settings.mixedPort.toInt(),
            httpPort = settings.httpPort?.toInt(),
            socksPort = settings.socksPort?.toInt(),
            language = languageLabel(AppPreferences.language(this)),
            appearance = appearanceLabel(AppPreferences.appearance(this)),
            uiVariant = uiVariantLabel(AppPreferences.uiVariant(this)),
            autoRestart = AppPreferences.autoRestart(this),
            hideAppIcon = AppPreferences.hideAppIcon(this),
            hideFromRecents = AppPreferences.hideFromRecents(this),
            showTrafficInNotification = AppPreferences.showTrafficInNotification(this),
            canChangeTrafficNotification = canChangeVpnSettings(),
            vpnSystemStatus = vpnPresentation.format(this),
            vpnSystemRestricted = vpnPresentation.restricted,
        )
    }

    private fun canChangeVpnSettings(): Boolean =
        backend.profileDeletionAvailability.value.let { availability ->
            VpnSettingsAvailabilityPolicy.canChangeSettings(
                availability.serviceState,
                availability.hasRunRequest,
            )
        }

    private companion object {
        const val DEFAULT_DNS_QUERY_NAME = "example.com"
        const val DEFAULT_DNS_RECORD_TYPE = "A"
    }
}
