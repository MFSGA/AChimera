package rs.chimera.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import rs.chimera.android.R
import rs.chimera.android.ui.components.DiagnosticsShareSetting
import rs.chimera.android.ui.components.ListenerPortsDialog
import rs.chimera.android.ui.components.SettingsResetDialog
import rs.chimera.android.ui.components.TextInfoDialog
import rs.chimera.android.ui.navigation.DefaultAppUiRouter
import rs.chimera.android.ui.preferences.UiVariant
import rs.chimera.android.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogsClick: () -> Unit,
    onAppFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel()
    val context = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showUiDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPortDialog by remember { mutableStateOf(false) }
    var showDnsDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showProvidersDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val vpnSystemPresentation = viewModel.vpnSystemStatus.resolvePresentation()
    val vpnSystemSummary = vpnSystemPresentation.format(context)

    viewModel.runtimeSettingError?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRuntimeSettingError,
            title = { Text(stringResource(R.string.settings_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissRuntimeSettingError) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    if (showInfoDialog) {
        TextInfoDialog(
            title = stringResource(R.string.about_title),
            content = stringResource(R.string.settings_known_issues),
            onDismiss = { showInfoDialog = false },
        )
    }

    if (showResetDialog) {
        SettingsResetDialog(
            onDismiss = { showResetDialog = false },
            onConfirm = {
                showResetDialog = false
                viewModel.resetRuntimeSettings()
            },
        )
    }

    if (showDnsDialog) {
        DnsDiagnosticsDialog(
            onDismiss = { showDnsDialog = false },
            onQuery = viewModel::queryDns,
        )
    }

    if (showRulesDialog) {
        RuleDiagnosticsDialog(
            onDismiss = { showRulesDialog = false },
            onLoad = viewModel::listRules,
        )
    }

    if (showProvidersDialog) {
        ProxyProviderDiagnosticsDialog(
            onDismiss = { showProvidersDialog = false },
            onLoad = viewModel::listProxyProviders,
            onUpdate = viewModel::updateProxyProvider,
            onHealthcheck = viewModel::healthcheckProxyProvider,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_screen)) },
                windowInsets = WindowInsets(),
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = stringResource(R.string.action_about),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            item {
                SectionHeader(text = stringResource(R.string.settings_general))
            }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = viewModel.getLanguageDisplayName(),
                        onClick = { showLanguageDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.AutoMirrored.Outlined.Subject,
                        title = stringResource(R.string.logs_screen),
                        subtitle = stringResource(R.string.settings_logs_summary),
                        onClick = onLogsClick,
                    )
                    SettingsItem(
                        icon = Icons.Default.Dns,
                        title = stringResource(R.string.dns_diagnostics_title),
                        subtitle = stringResource(R.string.dns_diagnostics_summary),
                        onClick = { showDnsDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.Rule,
                        title = stringResource(R.string.rules_diagnostics_title),
                        subtitle = stringResource(R.string.rules_diagnostics_summary),
                        onClick = { showRulesDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Default.Public,
                        title = stringResource(R.string.proxy_providers_title),
                        subtitle = stringResource(R.string.proxy_providers_summary),
                        onClick = { showProvidersDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Default.Apps,
                        title = stringResource(R.string.settings_app_filter),
                        subtitle = viewModel.getAppFilterSummary(),
                        onClick = onAppFilterClick,
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.cmfa_settings_network))
            }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.vpn_system_status_title),
                        subtitle = vpnSystemSummary,
                        subtitleColor = if (vpnSystemPresentation.restricted) {
                            MaterialTheme.colorScheme.error
                        } else {
                            null
                        },
                        onClick = {},
                        showChevron = false,
                    )
                    SettingsItem(
                        icon = Icons.Default.Lan,
                        title = stringResource(R.string.settings_listener_ports),
                        subtitle = stringResource(
                            R.string.cmfa_ports_summary,
                            viewModel.mixedPort.toInt(),
                            viewModel.httpPort?.toString() ?: "—",
                            viewModel.socksPort?.toString() ?: "—",
                        ),
                        onClick = { showPortDialog = true },
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Lan,
                        title = stringResource(R.string.settings_allow_lan),
                        subtitle = stringResource(R.string.settings_allow_lan_desc),
                        checked = viewModel.allowLan,
                        onCheckedChange = viewModel::updateAllowLan,
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Dns,
                        title = stringResource(R.string.settings_fake_ip),
                        subtitle = stringResource(R.string.settings_fake_ip_desc),
                        checked = viewModel.fakeIpEnabled,
                        onCheckedChange = viewModel::updateFakeIpEnabled,
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.Public,
                        title = stringResource(R.string.settings_ipv6),
                        subtitle = stringResource(R.string.settings_ipv6_desc),
                        checked = viewModel.ipv6Enabled,
                        onCheckedChange = viewModel::updateIpv6Enabled,
                    )
                    SettingsItem(
                        icon = Icons.Default.Refresh,
                        title = stringResource(R.string.settings_reset_network),
                        subtitle = stringResource(R.string.settings_reset_network_summary),
                        onClick = viewModel::resetNetwork,
                        enabled = !viewModel.networkResetInProgress,
                    )
                    SettingsItem(
                        icon = Icons.Default.Refresh,
                        title = stringResource(R.string.settings_reset_defaults),
                        subtitle = stringResource(R.string.settings_reset_defaults_summary),
                        onClick = { showResetDialog = true },
                    )
                }
            }

            item {
                SectionHeader(text = stringResource(R.string.settings_appearance))
            }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Default.DarkMode,
                        title = stringResource(R.string.settings_dark_mode),
                        subtitle = appearanceLabel(viewModel.appearancePreference),
                        onClick = { showAppearanceDialog = true },
                    )
                    SettingsItem(
                        icon = Icons.Default.Dashboard,
                        title = stringResource(R.string.settings_ui_style),
                        subtitle = uiVariantLabel(viewModel.uiVariant),
                        onClick = { showUiDialog = true },
                    )
                }
            }

            item { DiagnosticsShareSetting() }

            item {
                SectionHeader(text = stringResource(R.string.settings_about))
            }

            item {
                SettingsCard {
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        title = stringResource(R.string.about_title),
                        subtitle = stringResource(R.string.settings_about_summary),
                        onClick = { showInfoDialog = true },
                        showChevron = false,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        if (showPortDialog) {
            ListenerPortsDialog(
                currentMixedPort = viewModel.mixedPort,
                currentHttpPort = viewModel.httpPort,
                currentSocksPort = viewModel.socksPort,
                onDismiss = { showPortDialog = false },
                onConfirm = { mixedPort, httpPort, socksPort ->
                    showPortDialog = false
                    viewModel.updateListenerPorts(mixedPort, httpPort, socksPort)
                },
            )
        }

        if (showUiDialog) {
            UiDialog(
                currentVariant = viewModel.uiVariant,
                onDismiss = { showUiDialog = false },
                onConfirm = { variant ->
                    showUiDialog = false
                    if (viewModel.uiVariant != variant) {
                        viewModel.updateUiVariant(variant)
                        when (variant) {
                            UiVariant.WATFAQ -> DefaultAppUiRouter.openWatfaq(context)
                            UiVariant.METACUBEX -> DefaultAppUiRouter.openMetaCubeX(context)
                        }
                    }
                },
            )
        }

        if (showAppearanceDialog) {
            AppearanceDialog(
                currentPreference = viewModel.appearancePreference,
                onDismiss = { showAppearanceDialog = false },
                onConfirm = { preference ->
                    showAppearanceDialog = false
                    viewModel.updateAppearancePreference(preference)
                },
            )
        }

        if (showLanguageDialog) {
            LanguageDialog(
                currentPreference = viewModel.languagePreference,
                onDismiss = { showLanguageDialog = false },
                onConfirm = { preference ->
                    showLanguageDialog = false
                    viewModel.updateLanguagePreference(preference)
                },
            )
        }
    }
}
